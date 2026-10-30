package com.freshmart.wms.service;

import com.freshmart.wms.common.BizException;
import com.freshmart.wms.common.ErrorCode;
import com.freshmart.wms.common.PageResult;
import com.freshmart.wms.domain.entity.*;
import com.freshmart.wms.domain.enums.*;
import com.freshmart.wms.dto.ShortageHandleRequest;
import com.freshmart.wms.dto.ShortageQuery;
import com.freshmart.wms.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 缺货处理服务 —— 闭环中的异常分支。
 *
 * <p>拣货员在现场报缺后，缺货记录进入 {@code PENDING} 状态挂在波次上。
 * 波次员在缺货处理页面看到待处理列表，选择处理方式：
 *
 * <table border="1">
 *   <tr><th>处理方式</th><th>系统动作</th></tr>
 *   <tr><td>拆单出库 SPLIT</td><td>订单标记备注，缺货部分由客服后续补发</td></tr>
 *   <tr><td>取消该明细 CANCEL_ITEM</td><td>订单明细行取消，订单其余部分继续流转</td></tr>
 *   <tr><td>替代商品 SUBSTITUTE</td><td>记录替代 SKU，拣货明细行改挂替代商品</td></tr>
 *   <tr><td>等待补货 WAIT_REPLENISH</td><td>记录挂起说明，波次继续但标记待跟进</td></tr>
 *   <tr><td>整单取消 CANCEL_ORDER</td><td>关联订单置为已取消</td></tr>
 * </table>
 *
 * <p>缺货记录一旦处理完毕置为 {@code HANDLED}，不再计入待处理数。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShortageService {

    private final ShortageRepository shortageRepo;
    private final OrderRepository orderRepo;
    private final OrderItemRepository orderItemRepo;
    private final PickItemRepository pickItemRepo;
    private final OperationLogRepository logRepo;

    /* ====================================================================== */
    /* 查询                                                                    */
    /* ====================================================================== */

    public PageResult<ShortageRecord> pageShortages(ShortageQuery query) {
        List<ShortageRecord> all = shortageRepo.findAll().stream()
                .filter(s -> blank(query.getShortageNo())
                        || s.getShortageNo().toLowerCase().contains(query.getShortageNo().toLowerCase()))
                .filter(s -> blank(query.getWaveNo()) || query.getWaveNo().equals(s.getWaveNo()))
                .filter(s -> blank(query.getSkuCode()) || query.getSkuCode().equals(s.getSkuCode()))
                .filter(s -> blank(query.getStatus())
                        || (s.getStatus() != null && query.getStatus().equals(s.getStatus().getCode())))
                .filter(s -> blank(query.getReason())
                        || (s.getReason() != null && query.getReason().equals(s.getReason().getCode())))
                // 待处理排前面
                .sorted(Comparator.comparing((ShortageRecord s) -> s.getStatus() != ShortageStatus.PENDING)
                        .thenComparing(s -> s.getReportTime() == null ? LocalDateTime.MIN : s.getReportTime(),
                                Comparator.reverseOrder()))
                .collect(Collectors.toList());
        return PageResult.of(all, query);
    }

    public ShortageRecord getShortage(Long id) {
        return shortageRepo.findById(id)
                .orElseThrow(() -> new BizException(ErrorCode.SHORTAGE_NOT_FOUND));
    }

    /** 待处理缺货数，用于看板红点。 */
    public long pendingCount() {
        return shortageRepo.count(s -> s.getStatus() == ShortageStatus.PENDING);
    }

    /** 缺货原因分布，用于效率复盘定位根因。 */
    public Map<String, Long> reasonDistribution() {
        Map<String, Long> map = new LinkedHashMap<>();
        for (ShortageReason r : ShortageReason.values()) {
            long c = shortageRepo.count(s -> s.getReason() == r);
            if (c > 0) {
                map.put(r.getDesc(), c);
            }
        }
        return map;
    }

    /* ====================================================================== */
    /* 处理                                                                    */
    /* ====================================================================== */

    @Transactional
    public ShortageRecord handle(ShortageHandleRequest req) {
        ShortageRecord record = getShortage(req.getId());
        BizException.assertTrue(record.getStatus() == ShortageStatus.PENDING,
                ErrorCode.SHORTAGE_ALREADY_HANDLED, "该缺货记录已处理，不能重复处理");

        ShortageHandleType type;
        try {
            type = ShortageHandleType.of(req.getHandleType());
        } catch (IllegalArgumentException e) {
            throw new BizException(ErrorCode.SHORTAGE_HANDLE_TYPE_INVALID, "不支持的缺货处理方式: " + req.getHandleType());
        }

        // 提取为 final 局部变量：后续 record 会被重新赋值，lambda 只能捕获 final 变量
        final String taskNo = record.getTaskNo();
        final String skuCode = record.getSkuCode();
        final String locationCode = record.getLocationCode();
        final String orderNo = record.getOrderNo();
        final Integer shortageQty = record.getShortageQty();

        if (type == ShortageHandleType.SUBSTITUTE) {
            BizException.assertTrue(!blank(req.getSubstituteSkuCode()),
                    ErrorCode.PARAM_INVALID, "选择「替代商品」时必须指定替代 SKU");
            record.setSubstituteSkuCode(req.getSubstituteSkuCode());
            // 拣货明细行改挂替代 SKU，复核时按替代品核对
            pickItemRepo.findOne(i -> taskNo.equals(i.getTaskNo())
                            && skuCode.equals(i.getSkuCode())
                            && locationCode.equals(i.getLocationCode()))
                    .ifPresent(pi -> {
                        pi.setRemark("缺货替代：" + skuCode + " → " + req.getSubstituteSkuCode());
                        pickItemRepo.save(pi);
                    });
        }

        if (type == ShortageHandleType.CANCEL_ORDER && !blank(orderNo)) {
            orderRepo.findOne(o -> orderNo.equals(o.getOrderNo())).ifPresent(o -> {
                o.setStatus(OrderStatus.CANCELLED);
                orderRepo.save(o);
            });
        }

        if (type == ShortageHandleType.CANCEL_ITEM && !blank(orderNo)) {
            orderItemRepo.find(i -> orderNo.equals(i.getOrderNo())
                            && skuCode.equals(i.getSkuCode()))
                    .forEach(i -> {
                        i.setQty(0).setPickedQty(0).setRemark("缺货取消");
                        orderItemRepo.save(i);
                    });
        }

        if (type == ShortageHandleType.SPLIT && !blank(orderNo)) {
            orderRepo.findOne(o -> orderNo.equals(o.getOrderNo())).ifPresent(o -> {
                String tag = "[缺货拆单] " + skuCode + " 缺 " + shortageQty + " 件";
                o.setBuyerRemark(o.getBuyerRemark() == null ? tag : o.getBuyerRemark() + "；" + tag);
                orderRepo.save(o);
            });
        }

        record.setHandleType(type)
                .setHandlerNo(req.getHandlerNo())
                .setHandlerName(req.getHandlerName())
                .setHandleTime(LocalDateTime.now())
                .setHandleRemark(req.getHandleRemark())
                .setStatus(ShortageStatus.HANDLED);
        record = shortageRepo.save(record);

        saveLog("SHORTAGE", record.getShortageNo(), "SHORTAGE_HANDLE", "缺货处理",
                req.getHandlerNo(), req.getHandlerName(), "波次员",
                "处理方式=" + type.getDesc() + "；" + (req.getHandleRemark() == null ? "" : req.getHandleRemark()));
        log.info("[缺货处理] {} 波次 {} 采用「{}」", record.getShortageNo(), record.getWaveNo(), type.getDesc());
        return record;
    }

    /** 归档已处理的缺货记录。 */
    public ShortageRecord close(Long id) {
        ShortageRecord record = getShortage(id);
        BizException.assertTrue(record.getStatus() == ShortageStatus.HANDLED,
                ErrorCode.SHORTAGE_ALREADY_HANDLED, "只有已处理的记录可以归档");
        record.setStatus(ShortageStatus.CLOSED);
        return shortageRepo.save(record);
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
