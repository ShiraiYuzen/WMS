package com.example.wms.controller;

import com.example.wms.common.RequireRole;
import com.example.wms.dto.ApiResult;
import com.example.wms.dto.PageResult;
import com.example.wms.service.ShortageService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/shortage")
public class ShortageController {

    private final ShortageService shortageService;

    public ShortageController(ShortageService shortageService) {
        this.shortageService = shortageService;
    }

    @GetMapping("/pending-count")
    public ApiResult<Long> pendingCount() {
        return ApiResult.ok(shortageService.pendingCount());
    }

    @GetMapping("/reason-distribution")
    public ApiResult<Map<String, Object>> reasonDistribution() {
        return ApiResult.ok(shortageService.reasonDistribution());
    }

    @RequireRole({"ADMIN", "WAVE_OPERATOR"})
    @PostMapping("/handle")
    public ApiResult<Map<String, Object>> handle(@RequestBody Map<String, Object> body) {
        return ApiResult.ok(shortageService.handle(body));
    }

    @RequireRole({"ADMIN", "WAVE_OPERATOR"})
    @PostMapping("/{id}/close")
    public ApiResult<Map<String, Object>> close(@PathVariable long id) {
        return ApiResult.ok(shortageService.close(id));
    }

    @GetMapping("/page")
    public ApiResult<PageResult<Map<String, Object>>> page(
            @RequestParam(required = false) String shortageNo,
            @RequestParam(required = false) String waveNo,
            @RequestParam(required = false) String skuCode,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String reason,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        return ApiResult.ok(shortageService.page(shortageNo, waveNo, skuCode, status, reason, pageNum, pageSize));
    }
}
