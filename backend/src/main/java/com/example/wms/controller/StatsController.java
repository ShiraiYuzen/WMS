package com.example.wms.controller;

import com.example.wms.dto.ApiResult;
import com.example.wms.service.StatsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/stats")
public class StatsController {

    private final StatsService statsService;

    public StatsController(StatsService statsService) {
        this.statsService = statsService;
    }

    @GetMapping("/dashboard")
    public ApiResult<Map<String, Object>> dashboard() {
        return ApiResult.ok(statsService.dashboard());
    }

    @GetMapping("/efficiency")
    public ApiResult<Map<String, Object>> efficiency() {
        return ApiResult.ok(statsService.efficiency());
    }

    @GetMapping("/errors")
    public ApiResult<Map<String, Object>> errors() {
        return ApiResult.ok(statsService.errors());
    }
}
