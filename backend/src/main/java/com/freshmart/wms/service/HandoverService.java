package com.freshmart.wms.service;

import com.freshmart.wms.common.BizException;
import com.freshmart.wms.common.BizNoGenerator;
import com.freshmart.wms.common.ErrorCode;
import com.freshmart.wms.common.PageResult;
import com.freshmart.wms.domain.entity.*;
import com.freshmart.wms.domain.enums.HandoverStatus;
import com.freshmart.wms.domain.enums.OrderStatus;
import com.freshmart.wms.domain.enums.WaveStatus;
import com.freshmart.wms.dto.HandoverConfirmRequest;
import com.freshmart.wms.dto.HandoverCreateRequest;
import com.freshmart.wms.dto.HandoverQuery;
import com.freshmart.wms.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 出库交接服务 —— 闭环第五环「出库交接」。
 *
 * <p>复核完成的波次按承运商归集成交接单，仓库方与承运商司机共同清点件数后确认交接。
 * 交接确认后，波次置为「已出库交接」、订单置为「已出库」，整个作业闭环收口。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HandoverService {

    private final HandoverRepository handoverRepo;
    private final WaveRepository waveRepo;
    private final WaveOrderRepository waveOrderRepo;
    private final OrderRepository orderRepo;
    private final ReviewRepository reviewRepo;
    private final CarrierRepository carrierRepo;
    private final OperationLogRepository logRepo;
    private final WaveService waveService;
    private final BizNoGenerator noGen;

    /* ====================================================================== */
    /* 查询                                                                    */
    /* ====================================================================== */

    public PageResult<HandoverRecord> pageHandovers(HandoverQuery query) {
        List<HandoverRecord> all = handoverRepo.findAll().stream()
                .filter(h -> blank(query.getHandoverNo())
                        || h.getHandoverNo().toLowerCase().contains(query.getHandoverNo().toLowerCase()))
                .filter(h -> blank(query.getCarrierCode()) || query.getCarrierCode().equals(h.getCarrierCode()))
                .filter(h -> blank(query.getStatus())
                        || (h.getStatus() != null && query.getStatus().equals(h.getStatus().getCode())))
                .sorted(Comparator.comparing((HandoverRecord h) -> h.getStatus() != HandoverStatus.CREATED)
                        .thenComparing(h -> h.getCreateTime() == null ? LocalDateTime.MIN : h.getCreateTime(),
                                Comparator.reverseOrder()))
                .collect(Collectors.toList());
        return PageResult.of(all, query);
    }

    public HandoverRecord getHandover(String handoverNo) {
        return handoverRepo.findOne(h -> h.getHandoverNo().equals(handoverNo))
                .orElseThrow(() -> new BizException(ErrorCode.HANDOVER_NOT_FOUND));
    }

    /** 可交接的波次：已复核完成但尚未交接。 */
    public List<Wave> reviewableWaves(String carrierCode) {
        return waveRepo.find(w -> w.getStatus() == WaveStatus.REVIEWED).stream()
                .filter(w -> blank(carrierCode) || carrierCode.equals(carrierCodeOf(w.getWaveNo())))
                .sorted(Comparator.comparing(w -> w.getReviewFinishTime() == null
                        ? LocalDateTime.MIN : w.getReviewFinishTime()))
                .collect(Collectors.toList());
    }

    /* ====================================================================== */
    /* 创建与确认                                                               */
    /* ====================================================================== */

    /** 创建交接单：把若干「已复核」波次按承运商归集。 */
    @Transactional
    public HandoverRecord create(HandoverCreateRequest req) {
        Carrier carrier = carrierRepo.findOne(c -> c.getCarrierCode().equals(req.getCarrierCode()))
                .orElseThrow(() -> new BizException(ErrorCode.DATA_NOT_FOUND, "承运商不存在: " + req.getCarrierCode()));

        List<Wave> waves = new ArrayList<>();
        for (String waveNo : req.getWaveNos()) {
            Wave w = waveService.findWave(waveNo);
            BizException.assertTrue(w.getStatus() == WaveStatus.REVIEWED,
                    ErrorCode.HANDOVER_WAVE_NOT_READY,
                    "波次 " + waveNo + " 当前状态为「" + w.getStatus().getDesc() + "」，不能交接");
            waves.add(w);
        }

        int orderCount = 0;
        int packageCount = 0;
        double weight = 0.0;
        for (Wave w : waves) {
            List<ReviewRecord> reviews = reviewRepo.find(r -> w.getWaveNo().equals(r.getWaveNo()));
            orderCount += w.getOrderCount() == null ? 0 : w.getOrderCount();
            packageCount += reviews.stream()
                    .mapToInt(r -> r.getPackageCount() == null ? 1 : r.getPackageCount()).sum();
            weight += reviews.stream()
                    .mapToDouble(r -> r.getWeight() == null ? 0 : r.getWeight()).sum();
        }

        HandoverRecord ho = new HandoverRecord()
                .setHandoverNo(noGen.next("HO"))
                .setWaveNos(String.join(",", req.getWaveNos()))
                .setCarrierCode(carrier.getCarrierCode())
                .setCarrierName(carrier.getCarrierName())
                .setOrderCount(orderCount)
                .setPackageCount(packageCount)
                .setTotalWeight(Math.round(weight * 100) / 100.0)
                .setStatus(HandoverStatus.CREATED)
                .setHandoverUserNo(req.getHandoverUserNo())
                .setHandoverUserName(req.getHandoverUserName())
                .setVehicleNo(req.getVehicleNo())
                .setRemark(req.getRemark());
        ho.setCreateTime(LocalDateTime.now());
        ho = handoverRepo.save(ho);

        saveLog("HANDOVER", ho.getHandoverNo(), "HANDOVER_CREATE", "创建交接单",
                req.getHandoverUserNo(), req.getHandoverUserName(), "复核员",
                "承运商=" + carrier.getCarrierName() + "，波次=" + ho.getWaveNos()
                        + "，订单 " + orderCount + " 单 / 包裹 " + packageCount + " 件");
        log.info("[出库交接] 创建交接单 {} 承运商 {} 波次 {}", ho.getHandoverNo(),
                carrier.getCarrierName(), ho.getWaveNos());
        return ho;
    }

    /**
     * 确认交接：清点无误后收口。
     * 实交件数与系统件数不一致时，仍允许交接但会记录异常说明，供复盘追查。
     */
    @Transactional
    public HandoverRecord confirm(HandoverConfirmRequest req) {
        HandoverRecord ho = getHandover(req.getHandoverNo());
        BizException.assertTrue(ho.getStatus() == HandoverStatus.CREATED,
                ErrorCode.HANDOVER_STATUS_ILLEGAL, "该交接单已处理，不能重复确认");

        LocalDateTime now = LocalDateTime.now();
        if (Boolean.TRUE.equals(req.getRejected())) {
            ho.setStatus(HandoverStatus.REJECTED)
                    .setReceiveName(req.getReceiveName())
                    .setHandoverTime(now)
                    .setRemark(req.getRemark());
            handoverRepo.save(ho);
            saveLog("HANDOVER", ho.getHandoverNo(), "HANDOVER_REJECT", "承运商拒收",
                    ho.getHandoverUserNo(), ho.getHandoverUserName(), "复核员",
                    req.getRemark() == null ? "" : req.getRemark());
            return ho;
        }

        if (req.getActualPackageCount() != null && !req.getActualPackageCount().equals(ho.getPackageCount())) {
            String diff = "实交 " + req.getActualPackageCount() + " 件，系统 " + ho.getPackageCount() + " 件";
            ho.setRemark((ho.getRemark() == null ? "" : ho.getRemark() + "；") + "[件数差异] " + diff);
            log.warn("[出库交接] {} 件数差异：{}", ho.getHandoverNo(), diff);
        }

        ho.setStatus(HandoverStatus.CONFIRMED)
                .setReceiveName(req.getReceiveName())
                .setHandoverTime(now)
                .setPackageCount(req.getActualPackageCount() == null ? ho.getPackageCount() : req.getActualPackageCount());
        if (!blank(req.getRemark())) {
            ho.setRemark((ho.getRemark() == null ? "" : ho.getRemark() + "；") + req.getRemark());
        }
        ho = handoverRepo.save(ho);

        // 推进波次与订单状态
        for (String waveNo : Arrays.asList(ho.getWaveNos().split(","))) {
            waveService.markHandovered(waveNo.trim(), now);
            for (WaveOrder wo : waveOrderRepo.find(r -> waveNo.trim().equals(r.getWaveNo()))) {
                orderRepo.findOne(o -> o.getOrderNo().equals(wo.getOrderNo())).ifPresent(o -> {
                    o.setStatus(OrderStatus.OUTBOUND);
                    orderRepo.save(o);
                });
            }
        }

        saveLog("HANDOVER", ho.getHandoverNo(), "HANDOVER_CONFIRM", "交接完成",
                ho.getHandoverUserNo(), ho.getHandoverUserName(), "复核员",
                "接收人=" + req.getReceiveName() + "，件数 " + ho.getPackageCount());
        log.info("[出库交接] {} 交接完成，共 {} 件", ho.getHandoverNo(), ho.getPackageCount());
        return ho;
    }

    /* ====================================================================== */
    /* 内部工具                                                                 */
    /* ====================================================================== */

    /** 取波次的承运商编码（取该波次首单的承运商）。 */
    private String carrierCodeOf(String waveNo) {
        return waveOrderRepo.find(r -> waveNo.equals(r.getWaveNo())).stream()
                .findFirst()
                .flatMap(r -> orderRepo.findOne(o -> o.getOrderNo().equals(r.getOrderNo())))
                .map(OrderInfo::getCarrierCode)
                .orElse(null);
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
