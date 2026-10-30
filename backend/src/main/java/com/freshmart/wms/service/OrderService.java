package com.freshmart.wms.service;

import com.freshmart.wms.common.BizException;
import com.freshmart.wms.common.ErrorCode;
import com.freshmart.wms.common.PageResult;
import com.freshmart.wms.domain.entity.OrderInfo;
import com.freshmart.wms.domain.entity.OrderItem;
import com.freshmart.wms.domain.enums.OrderStatus;
import com.freshmart.wms.dto.OrderPoolRequest;
import com.freshmart.wms.dto.OrderQuery;
import com.freshmart.wms.repository.OrderItemRepository;
import com.freshmart.wms.repository.OrderRepository;
import com.freshmart.wms.vo.OrderVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 订单服务 —— 闭环第一环「订单汇聚」。
 *
 * <p>订单从平台推送进来时为 {@code CREATED}（新建待汇聚）。
 * 波次员在订单池页面勾选订单后调用 {@link #poolOrders}，
 * 订单进入 {@code POOLED}（已汇聚入池）状态，等待波次生成。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepo;
    private final OrderItemRepository orderItemRepo;

    /* ---------------- 查询 ---------------- */

    public PageResult<OrderVO> pageOrders(OrderQuery query) {
        List<OrderVO> all = orderRepo.findAll().stream()
                .filter(o -> matchKeyword(o, query.getOrderNo()))
                .filter(o -> blank(query.getPlatform()) || query.getPlatform().equals(o.getPlatform()))
                .filter(o -> blank(query.getCarrierCode()) || query.getCarrierCode().equals(o.getCarrierCode()))
                .filter(o -> blank(query.getStatus()) || (o.getStatus() != null && query.getStatus().equals(o.getStatus().getCode())))
                .filter(o -> blank(query.getProvince()) || query.getProvince().equals(o.getProvince()))
                .filter(o -> blank(query.getPriority()) || query.getPriority().equals(o.getPriority()))
                .filter(o -> blank(query.getWaveNo()) || query.getWaveNo().equals(o.getWaveNo()))
                .filter(o -> query.getStartDate() == null || (o.getPayTime() != null
                        && !o.getPayTime().toLocalDate().isBefore(query.getStartDate())))
                .filter(o -> query.getEndDate() == null || (o.getPayTime() != null
                        && !o.getPayTime().toLocalDate().isAfter(query.getEndDate())))
                .map(this::toVO)
                .collect(Collectors.toList());

        // 排序：加急优先，其次支付时间倒序
        all.sort(Comparator.comparing((OrderVO v) -> !"URGENT".equals(v.getPriority()))
                .thenComparing(v -> v.getPayTime() == null ? LocalDateTime.MIN : v.getPayTime(),
                        Comparator.reverseOrder()));

        return PageResult.of(all, query);
    }

    public OrderVO getOrder(String orderNo) {
        OrderInfo order = orderRepo.findOne(o -> o.getOrderNo().equals(orderNo))
                .orElseThrow(() -> new BizException(ErrorCode.ORDER_NOT_FOUND));
        return toVO(order);
    }

    /** 按状态统计订单数，供看板使用。 */
    public Map<String, Long> countByStatus() {
        Map<String, Long> map = new LinkedHashMap<>();
        for (OrderStatus s : OrderStatus.values()) {
            map.put(s.getCode(), orderRepo.count(o -> o.getStatus() == s));
        }
        return map;
    }

    /* ---------------- 汇聚 ---------------- */

    /**
     * 批量汇聚：把选中的订单从「新建待汇聚」推进到「已汇聚入池」。
     *
     * @return 实际汇聚成功的订单数
     */
    public int poolOrders(OrderPoolRequest request) {
        List<OrderInfo> targets = orderRepo.findAll().stream()
                .filter(o -> request.getOrderNos().contains(o.getOrderNo()))
                .filter(o -> o.getStatus() != null && o.getStatus().isPoolable())
                .collect(Collectors.toList());

        BizException.assertTrue(!targets.isEmpty(), ErrorCode.ORDER_POOL_EMPTY);

        for (OrderInfo o : targets) {
            o.setStatus(OrderStatus.POOLED);
            orderRepo.save(o);
        }
        log.info("[订单汇聚] 汇聚 {} 笔订单: {}", targets.size(),
                targets.stream().map(OrderInfo::getOrderNo).collect(Collectors.joining(",")));
        return targets.size();
    }

    /** 一键汇聚全部可汇聚订单。 */
    public int poolAll() {
        List<OrderInfo> targets = orderRepo.find(o -> o.getStatus() == OrderStatus.CREATED);
        targets.forEach(o -> o.setStatus(OrderStatus.POOLED));
        orderRepo.saveAll(targets);
        log.info("[订单汇聚] 一键汇聚 {} 笔订单", targets.size());
        return targets.size();
    }

    /** 取消订单：仅允许在未进入波次前取消。 */
    public OrderVO cancelOrder(String orderNo) {
        OrderInfo order = orderRepo.findOne(o -> o.getOrderNo().equals(orderNo))
                .orElseThrow(() -> new BizException(ErrorCode.ORDER_NOT_FOUND));
        BizException.assertTrue(order.getStatus() != null && order.getStatus().isPoolable(),
                ErrorCode.ORDER_STATUS_ILLEGAL, "订单已进入波次，不能取消");
        order.setStatus(OrderStatus.CANCELLED);
        return toVO(orderRepo.save(order));
    }

    /* ---------------- 内部工具 ---------------- */

    /** 查询可参与波次生成的订单（已汇聚入池）。 */
    public List<OrderInfo> findPooledOrders() {
        return orderRepo.find(o -> o.getStatus() == OrderStatus.POOLED);
    }

    public List<OrderItem> findItems(String orderNo) {
        return orderItemRepo.find(i -> i.getOrderNo().equals(orderNo))
                .stream().sorted(Comparator.comparing(OrderItem::getLineNo))
                .collect(Collectors.toList());
    }

    public OrderVO toVO(OrderInfo order) {
        OrderVO vo = new OrderVO();
        vo.setId(order.getId());
        vo.setCreateTime(order.getCreateTime());
        vo.setUpdateTime(order.getUpdateTime());
        vo.setDeleted(order.getDeleted());
        vo.setOrderNo(order.getOrderNo());
        vo.setPlatform(order.getPlatform());
        vo.setCarrierCode(order.getCarrierCode());
        vo.setCarrierName(order.getCarrierName());
        vo.setReceiverName(order.getReceiverName());
        vo.setReceiverPhone(order.getReceiverPhone());
        vo.setReceiverAddress(order.getReceiverAddress());
        vo.setProvince(order.getProvince());
        vo.setCity(order.getCity());
        vo.setPriority(order.getPriority());
        vo.setExpectedDeliveryTime(order.getExpectedDeliveryTime());
        vo.setPayTime(order.getPayTime());
        vo.setStatus(order.getStatus());
        vo.setWaveNo(order.getWaveNo());
        vo.setSkuCount(order.getSkuCount());
        vo.setTotalQty(order.getTotalQty());
        vo.setAmount(order.getAmount());
        vo.setBuyerRemark(order.getBuyerRemark());

        vo.setStatusDesc(order.getStatus() == null ? "" : order.getStatus().getDesc());
        vo.setPriorityDesc("URGENT".equals(order.getPriority()) ? "加急" : "普通");
        vo.setPoolable(order.getStatus() != null && order.getStatus().isPoolable());
        vo.setItems(findItems(order.getOrderNo()));
        return vo;
    }

    private boolean matchKeyword(OrderInfo o, String keyword) {
        return blank(keyword)
                || o.getOrderNo().toLowerCase().contains(keyword.toLowerCase())
                || (o.getReceiverName() != null && o.getReceiverName().contains(keyword));
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
