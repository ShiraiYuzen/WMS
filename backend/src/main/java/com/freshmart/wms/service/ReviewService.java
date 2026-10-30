package com.freshmart.wms.service;

import com.freshmart.wms.common.BizException;
import com.freshmart.wms.common.BizNoGenerator;
import com.freshmart.wms.common.ErrorCode;
import com.freshmart.wms.common.PageResult;
import com.freshmart.wms.domain.entity.*;
import com.freshmart.wms.domain.enums.DiffType;
import com.freshmart.wms.domain.enums.OrderStatus;
import com.freshmart.wms.domain.enums.ReviewStatus;
import com.freshmart.wms.domain.enums.WaveStatus;
import com.freshmart.wms.dto.ReviewQuery;
import com.freshmart.wms.dto.ReviewSubmitRequest;
import com.freshmart.wms.repository.*;
import com.freshmart.wms.vo.ReviewVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 复核服务 —— 闭环第四环「复核打包」。
 *
 * <p>复核员认领一个「拣货完成」的波次后，系统为该波次内每笔订单生成一条待复核记录。
 * 复核员逐单核对商品与数量，提交结果时若存在差异，需逐条登记差异明细
 * ——这些明细就是差错率统计的原始数据。
 *
 * <p>当波次内全部订单复核完毕，波次自动推进到「复核完成」，进入出库交接环节。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewService {

    private final ReviewRepository reviewRepo;
    private final ReviewDiffRepository diffRepo;
    private final WaveRepository waveRepo;
    private final WaveOrderRepository waveOrderRepo;
    private final OrderRepository orderRepo;
    private final OrderItemRepository orderItemRepo;
    private final PickItemRepository pickItemRepo;
    private final OperationLogRepository logRepo;
    private final WaveService waveService;
    private final BizNoGenerator noGen;

    /* ====================================================================== */
    /* 查询                                                                    */
    /* ====================================================================== */

    public PageResult<ReviewVO> pageReviews(ReviewQuery query) {
        List<ReviewVO> all = reviewRepo.findAll().stream()
                .filter(r -> blank(query.getReviewNo())
                        || r.getReviewNo().toLowerCase().contains(query.getReviewNo().toLowerCase()))
                .filter(r -> blank(query.getWaveNo()) || query.getWaveNo().equals(r.getWaveNo()))
                .filter(r -> blank(query.getOrderNo()) || query.getOrderNo().equals(r.getOrderNo()))
                .filter(r -> blank(query.getStatus())
                        || (r.getStatus() != null && query.getStatus().equals(r.getStatus().getCode())))
                .filter(r -> blank(query.getDiffType())
                        || (r.getDiffType() != null && query.getDiffType().equals(r.getDiffType().getCode())))
                .filter(r -> blank(query.getReviewerNo()) || query.getReviewerNo().equals(r.getReviewerNo()))
                .sorted(Comparator.comparing((ReviewRecord r) -> r.getStatus() != ReviewStatus.WAITING)
                        .thenComparing(r -> r.getFinishTime() == null ? LocalDateTime.MIN : r.getFinishTime(),
                                Comparator.reverseOrder()))
                .map(this::toVO)
                .collect(Collectors.toList());
        return PageResult.of(all, query);
    }

    public ReviewVO getReview(String reviewNo) {
        return toVO(findReview(reviewNo));
    }

    /** 待复核波次列表：拣货完成但尚未复核完成的波次。 */
    public List<Wave> pendingReviewWaves() {
        return waveRepo.find(w -> w.getStatus() == WaveStatus.PICKED || w.getStatus() == WaveStatus.REVIEWING)
                .stream()
                .sorted(Comparator.comparing(w -> w.getPickFinishTime() == null
                        ? LocalDateTime.MIN : w.getPickFinishTime()))
                .collect(Collectors.toList());
    }

    /* ====================================================================== */
    /* 认领与提交                                                               */
    /* ====================================================================== */

    /**
     * 复核员认领波次：为波次内每笔订单生成待复核记录。
     * 若该波次已被认领，则直接返回已有记录，不重复生成。
     */
    @Transactional
    public List<ReviewVO> claimWave(String waveNo, String reviewerNo, String reviewerName) {
        Wave wave = waveService.findWave(waveNo);
        BizException.assertTrue(wave.getStatus() == WaveStatus.PICKED || wave.getStatus() == WaveStatus.REVIEWING,
                ErrorCode.WAVE_STATUS_ILLEGAL, "只有「拣货完成」的波次可以进入复核");

        List<ReviewRecord> exists = reviewRepo.find(r -> waveNo.equals(r.getWaveNo()));
        if (exists.isEmpty()) {
            for (WaveOrder wo : waveOrderRepo.find(r -> waveNo.equals(r.getWaveNo()))) {
                List<OrderItem> items = orderItemRepo.find(i -> wo.getOrderNo().equals(i.getOrderNo()))
                        .stream().sorted(Comparator.comparing(OrderItem::getLineNo))
                        .collect(Collectors.toList());
                int shouldQty = items.stream().mapToInt(OrderItem::getQty).sum();
                int actualQty = items.stream()
                        .mapToInt(i -> i.getPickedQty() == null ? 0 : i.getPickedQty()).sum();

                ReviewRecord rr = new ReviewRecord()
                        .setReviewNo(noGen.next("RV"))
                        .setWaveNo(waveNo)
                        .setOrderNo(wo.getOrderNo())
                        .setReviewerNo(reviewerNo)
                        .setReviewerName(reviewerName)
                        .setStatus(ReviewStatus.WAITING)
                        .setShouldSkuCount(items.size())
                        .setShouldQty(shouldQty)
                        .setActualQty(actualQty)
                        .setDiffType(DiffType.NONE)
                        .setDiffQty(0)
                        .setPackageCount(1);
                rr.setCreateTime(LocalDateTime.now());
                reviewRepo.save(rr);
            }
            wave.setStatus(WaveStatus.REVIEWING)
                    .setReviewerNo(reviewerNo)
                    .setReviewerName(reviewerName);
            waveRepo.save(wave);
            saveLog("REVIEW", waveNo, "REVIEW_CLAIM", "认领复核",
                    reviewerNo, reviewerName, "复核员", "波次 " + waveNo);
        }
        return reviewRepo.find(r -> waveNo.equals(r.getWaveNo()))
                .stream().map(this::toVO).collect(Collectors.toList());
    }

    /**
     * 提交复核结果。若有差异，逐条登记差异明细，并据此判定差异类型。
     */
    @Transactional
    public ReviewVO submit(ReviewSubmitRequest req) {
        ReviewRecord record = findReview(req.getReviewNo());
        BizException.assertTrue(record.getStatus() == ReviewStatus.WAITING
                        || record.getStatus() == ReviewStatus.DIFF,
                ErrorCode.REVIEW_STATUS_ILLEGAL, "该复核单已提交，不能重复提交");

        // 提取为 final 局部变量：record 后续会被重新赋值，lambda 只能捕获 final 变量
        final String reviewNo = record.getReviewNo();
        final String orderNo = record.getOrderNo();
        final String waveNo = record.getWaveNo();

        LocalDateTime now = LocalDateTime.now();
        if (record.getStartTime() == null) {
            record.setStartTime(now);
        }
        record.setFinishTime(now)
                .setDurationSec(Duration.between(record.getStartTime(), now).getSeconds())
                .setReviewerNo(blank(req.getReviewerNo()) ? record.getReviewerNo() : req.getReviewerNo())
                .setReviewerName(blank(req.getReviewerName()) ? record.getReviewerName() : req.getReviewerName())
                .setPackMaterial(blank(req.getPackMaterial()) ? record.getPackMaterial() : req.getPackMaterial())
                .setPackageCount(req.getPackageCount() == null ? 1 : req.getPackageCount())
                .setWeight(req.getWeight())
                .setRemark(req.getRemark());

        if (req.getActualQty() != null) {
            record.setActualQty(req.getActualQty());
        }

        // 清理旧的差异明细（支持复核员修正后重提）
        diffRepo.find(d -> reviewNo.equals(d.getReviewNo()))
                .forEach(d -> diffRepo.deleteById(d.getId()));

        List<ReviewSubmitRequest.DiffItemDTO> diffItems = req.getDiffItems() == null
                ? new ArrayList<>() : req.getDiffItems();

        if (diffItems.isEmpty()) {
            record.setStatus(ReviewStatus.PASS).setDiffType(DiffType.NONE).setDiffQty(0).setDiffDetail(null);
        } else {
            int totalDiff = 0;
            List<String> descs = new ArrayList<>();
            DiffType primary = DiffType.NONE;
            for (ReviewSubmitRequest.DiffItemDTO d : diffItems) {
                DiffType type = DiffType.of(d.getDiffType() == null ? "SHORT" : d.getDiffType());
                int shouldQty = d.getShouldQty() == null ? 0 : d.getShouldQty();
                int actualQty = d.getActualQty() == null ? 0 : d.getActualQty();
                int diffQty = actualQty - shouldQty;

                diffRepo.save(new ReviewDiffItem()
                        .setReviewNo(record.getReviewNo())
                        .setWaveNo(record.getWaveNo())
                        .setOrderNo(record.getOrderNo())
                        .setSkuCode(d.getSkuCode())
                        .setSkuName(d.getSkuName())
                        .setDiffType(type)
                        .setShouldQty(shouldQty)
                        .setActualQty(actualQty)
                        .setDiffQty(diffQty)
                        .setResponsibleStage(d.getResponsibleStage())
                        .setResponsibleNo(d.getResponsibleNo())
                        .setRemark(d.getRemark()));

                totalDiff += Math.abs(diffQty);
                descs.add(type.getDesc() + " " + d.getSkuName() + "（应 " + shouldQty + " 实 " + actualQty + "）");
                if (primary == DiffType.NONE) {
                    primary = type;
                }
            }
            record.setStatus(ReviewStatus.DIFF).setDiffType(primary).setDiffQty(totalDiff)
                    .setDiffDetail(String.join("；", descs));
        }
        record = reviewRepo.save(record);

        // 订单推进到已复核
        orderRepo.findOne(o -> orderNo.equals(o.getOrderNo())).ifPresent(o -> {
            o.setStatus(OrderStatus.REVIEWED);
            orderRepo.save(o);
        });

        // 波次内全部订单复核完毕 → 波次推进
        List<ReviewRecord> all = reviewRepo.find(r -> waveNo.equals(r.getWaveNo()));
        boolean allDone = !all.isEmpty() && all.stream()
                .noneMatch(r -> r.getStatus() == ReviewStatus.WAITING);
        if (allDone) {
            waveService.markReviewed(waveNo);
            saveLog("REVIEW", waveNo, "REVIEW_FINISH", "复核完成",
                    record.getReviewerNo(), record.getReviewerName(), "复核员",
                    "波次 " + waveNo + " 共复核 " + all.size() + " 单");
        }

        saveLog("REVIEW", reviewNo, "REVIEW_SUBMIT", "提交复核",
                record.getReviewerNo(), record.getReviewerName(), "复核员",
                record.getStatus() == ReviewStatus.PASS ? "复核通过"
                        : "存在差异：" + record.getDiffDetail());
        return toVO(record);
    }

    /* ====================================================================== */
    /* 内部工具                                                                 */
    /* ====================================================================== */

    private ReviewRecord findReview(String reviewNo) {
        return reviewRepo.findOne(r -> r.getReviewNo().equals(reviewNo))
                .orElseThrow(() -> new BizException(ErrorCode.REVIEW_NOT_FOUND, "复核单不存在: " + reviewNo));
    }

    public ReviewVO toVO(ReviewRecord r) {
        ReviewVO vo = new ReviewVO();
        vo.setId(r.getId());
        vo.setCreateTime(r.getCreateTime());
        vo.setUpdateTime(r.getUpdateTime());
        vo.setDeleted(r.getDeleted());
        vo.setReviewNo(r.getReviewNo());
        vo.setWaveNo(r.getWaveNo());
        vo.setOrderNo(r.getOrderNo());
        vo.setReviewerNo(r.getReviewerNo());
        vo.setReviewerName(r.getReviewerName());
        vo.setStatus(r.getStatus());
        vo.setShouldSkuCount(r.getShouldSkuCount());
        vo.setShouldQty(r.getShouldQty());
        vo.setActualQty(r.getActualQty());
        vo.setDiffType(r.getDiffType());
        vo.setDiffQty(r.getDiffQty());
        vo.setDiffDetail(r.getDiffDetail());
        vo.setPackMaterial(r.getPackMaterial());
        vo.setPackageCount(r.getPackageCount());
        vo.setWeight(r.getWeight());
        vo.setStartTime(r.getStartTime());
        vo.setFinishTime(r.getFinishTime());
        vo.setDurationSec(r.getDurationSec());
        vo.setRemark(r.getRemark());

        vo.setStatusDesc(r.getStatus() == null ? "" : r.getStatus().getDesc());
        vo.setDiffTypeDesc(r.getDiffType() == null ? "" : r.getDiffType().getDesc());
        vo.setShouldItems(orderItemRepo.find(i -> r.getOrderNo().equals(i.getOrderNo()))
                .stream().sorted(Comparator.comparing(OrderItem::getLineNo))
                .collect(Collectors.toList()));
        vo.setDiffItems(diffRepo.find(d -> r.getReviewNo().equals(d.getReviewNo())));
        return vo;
    }

    private void saveLog(String bizType, String bizNo, String action, String actionDesc,
                         String operatorNo, String operatorName, String operatorRole, String detail) {
        OperationLog ol = new OperationLog()
                .setBizType(bizType).setBizNo(bizNo).setAction(action).setActionDesc(actionDesc)
                .setOperatorNo(operatorNo).setOperatorName(operatorName).setOperatorRole(operatorRole)
                .setDetail(detail).setOperateTime(LocalDateTime.now());
        ol.setCreateTime(LocalDateTime.now());
        logRepo.save(ol);
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
