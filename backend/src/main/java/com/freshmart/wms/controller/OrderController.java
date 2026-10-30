package com.freshmart.wms.controller;

import com.freshmart.wms.common.PageResult;
import com.freshmart.wms.common.R;
import com.freshmart.wms.dto.OrderPoolRequest;
import com.freshmart.wms.dto.OrderQuery;
import com.freshmart.wms.service.OrderService;
import com.freshmart.wms.vo.OrderVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 订单接口 —— 闭环第一环「订单汇聚」。
 */
@Tag(name = "01. 订单汇聚", description = "订单查询、汇聚入池、取消")
@RestController
@RequestMapping("/api/order")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @Operation(summary = "订单分页查询")
    @GetMapping("/page")
    public R<PageResult<OrderVO>> page(OrderQuery query) {
        return R.ok(orderService.pageOrders(query));
    }

    @Operation(summary = "订单详情（含商品明细）")
    @GetMapping("/{orderNo}")
    public R<OrderVO> detail(@PathVariable String orderNo) {
        return R.ok(orderService.getOrder(orderNo));
    }

    @Operation(summary = "按状态统计订单数")
    @GetMapping("/count-by-status")
    public R<Map<String, Long>> countByStatus() {
        return R.ok(orderService.countByStatus());
    }

    @Operation(summary = "汇聚选中订单入池", description = "把「新建待汇聚」的订单推进到「已汇聚入池」，等待生成波次")
    @PostMapping("/pool")
    public R<Integer> pool(@Valid @RequestBody OrderPoolRequest request) {
        int count = orderService.poolOrders(request);
        return R.ok(count, "已汇聚 " + count + " 笔订单");
    }

    @Operation(summary = "一键汇聚全部待汇聚订单")
    @PostMapping("/pool-all")
    public R<Integer> poolAll() {
        int count = orderService.poolAll();
        return R.ok(count, "已汇聚 " + count + " 笔订单");
    }

    @Operation(summary = "取消订单", description = "仅允许在订单进入波次前取消")
    @PostMapping("/{orderNo}/cancel")
    public R<OrderVO> cancel(@PathVariable String orderNo) {
        return R.ok(orderService.cancelOrder(orderNo), "订单已取消");
    }
}
