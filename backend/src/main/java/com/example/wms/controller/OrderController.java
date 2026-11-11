package com.example.wms.controller;

import com.example.wms.common.RequireRole;
import com.example.wms.dto.ApiResult;
import com.example.wms.dto.PageResult;
import com.example.wms.service.OrderService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/order")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping("/count-by-status")
    public ApiResult<Map<String, Object>> countByStatus() {
        return ApiResult.ok(orderService.countByStatus());
    }

    @GetMapping("/page")
    public ApiResult<PageResult<Map<String, Object>>> page(
            @RequestParam(required = false) String orderNo,
            @RequestParam(required = false) String platform,
            @RequestParam(required = false) String carrierCode,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String province,
            @RequestParam(required = false) String priority,
            @RequestParam(required = false) String waveNo,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        return ApiResult.ok(orderService.page(orderNo, platform, carrierCode, status, province,
                priority, waveNo, startDate, endDate, pageNum, pageSize));
    }

    @RequireRole({"ADMIN", "WAVE_OPERATOR"})
    @PostMapping("/pool")
    public ApiResult<Integer> pool(@RequestBody Map<String, Object> body) {
        return ApiResult.ok(orderService.pool(body));
    }

    @RequireRole({"ADMIN", "WAVE_OPERATOR"})
    @PostMapping("/pool-all")
    public ApiResult<Integer> poolAll() {
        return ApiResult.ok(orderService.poolAll());
    }

    @RequireRole({"ADMIN", "WAVE_OPERATOR"})
    @PostMapping("/{orderNo}/cancel")
    public ApiResult<Map<String, Object>> cancel(@PathVariable String orderNo) {
        return ApiResult.ok(orderService.cancel(orderNo));
    }

    @GetMapping("/{orderNo}")
    public ApiResult<Map<String, Object>> detail(@PathVariable String orderNo) {
        return ApiResult.ok(orderService.detail(orderNo));
    }
}
