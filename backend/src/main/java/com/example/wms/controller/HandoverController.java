package com.example.wms.controller;

import com.example.wms.common.RequireRole;
import com.example.wms.dto.ApiResult;
import com.example.wms.dto.PageResult;
import com.example.wms.service.HandoverService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/handover")
public class HandoverController {

    private final HandoverService handoverService;

    public HandoverController(HandoverService handoverService) {
        this.handoverService = handoverService;
    }

    @RequireRole({"ADMIN", "REVIEWER"})
    @GetMapping("/reviewable-waves")
    public ApiResult<List<Map<String, Object>>> reviewableWaves(@RequestParam(required = false) String carrierCode) {
        return ApiResult.ok(handoverService.reviewableWaves(carrierCode));
    }

    @RequireRole({"ADMIN", "REVIEWER"})
    @PostMapping("/create")
    public ApiResult<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        return ApiResult.ok(handoverService.create(body));
    }

    @RequireRole({"ADMIN", "REVIEWER"})
    @PostMapping("/confirm")
    public ApiResult<Map<String, Object>> confirm(@RequestBody Map<String, Object> body) {
        return ApiResult.ok(handoverService.confirm(body));
    }

    @GetMapping("/page")
    public ApiResult<PageResult<Map<String, Object>>> page(
            @RequestParam(required = false) String handoverNo,
            @RequestParam(required = false) String carrierCode,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        return ApiResult.ok(handoverService.page(handoverNo, carrierCode, status, pageNum, pageSize));
    }
}
