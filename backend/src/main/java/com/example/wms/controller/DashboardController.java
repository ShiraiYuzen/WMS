package com.example.wms.controller;

import com.example.wms.dto.ApiResult;
import com.example.wms.service.DashboardService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/summary")
    public ApiResult<Map<String, Object>> summary(@RequestParam(defaultValue = "7") int days) {
        return ApiResult.ok(dashboardService.summary(days));
    }

    @GetMapping("/trend")
    public ApiResult<List<Map<String, Object>>> trend(@RequestParam(defaultValue = "7") int days) {
        return ApiResult.ok(dashboardService.trend(days));
    }
}
