package com.example.wms.controller;

import com.example.wms.dto.ApiResult;
import com.example.wms.service.Stats;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {

    private final Stats stats;

    public AnalyticsController(Stats stats) {
        this.stats = stats;
    }

    @GetMapping("/overview")
    public ApiResult<Map<String, Object>> overview(@RequestParam(required = false) String startDate,
                                                   @RequestParam(required = false) String endDate) {
        return ApiResult.ok(stats.overview(startDate, endDate));
    }

    @GetMapping("/employee-efficiency")
    public ApiResult<List<Map<String, Object>>> employeeEfficiency(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) String employeeNo) {
        return ApiResult.ok(stats.employeeEfficiency(startDate, endDate, employeeNo));
    }

    @GetMapping("/error-rate")
    public ApiResult<Map<String, Object>> errorRate(@RequestParam(required = false) String startDate,
                                                    @RequestParam(required = false) String endDate) {
        return ApiResult.ok(stats.errorRate(startDate, endDate));
    }

    @GetMapping("/trend")
    public ApiResult<List<Map<String, Object>>> trend(@RequestParam(required = false) String startDate,
                                                      @RequestParam(required = false) String endDate) {
        return ApiResult.ok(stats.trend(startDate, endDate));
    }

    @GetMapping("/shortage-distribution")
    public ApiResult<List<Map<String, Object>>> shortageDistribution(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        return ApiResult.ok(stats.shortageDistribution(startDate, endDate));
    }
}
