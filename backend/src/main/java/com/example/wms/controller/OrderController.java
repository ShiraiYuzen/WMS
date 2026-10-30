package com.example.wms.controller;

import com.example.wms.dto.ApiResult;
import com.example.wms.dto.OrderCreateRequest;
import com.example.wms.dto.PageResult;
import com.example.wms.entity.Order;
import com.example.wms.service.OrderService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/order")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping("/list")
    public ApiResult<PageResult<Order>> list(@RequestParam(required = false) String status,
                                             @RequestParam(required = false) String platform,
                                             @RequestParam(required = false) String keyword,
                                             @RequestParam(defaultValue = "1") int page,
                                             @RequestParam(defaultValue = "10") int size) {
        return ApiResult.ok(orderService.list(status, platform, keyword, page, size));
    }

    @GetMapping("/detail/{id}")
    public ApiResult<Map<String, Object>> detail(@PathVariable Long id) {
        return ApiResult.ok(orderService.detail(id));
    }

    @GetMapping("/pending")
    public ApiResult<List<Order>> pending() {
        return ApiResult.ok(orderService.pending());
    }

    @PostMapping("/add")
    public ApiResult<Order> add(@RequestBody OrderCreateRequest req) {
        return ApiResult.ok(orderService.add(req));
    }

    @PostMapping("/cancel")
    public ApiResult<Void> cancel(@RequestBody Map<String, Object> body) {
        Object id = body.get("id");
        orderService.cancel(id == null ? null : Long.valueOf(id.toString()));
        return ApiResult.ok();
    }
}
