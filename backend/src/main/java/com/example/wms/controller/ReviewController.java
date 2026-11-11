package com.example.wms.controller;

import com.example.wms.common.RequireRole;
import com.example.wms.dto.ApiResult;
import com.example.wms.dto.PageResult;
import com.example.wms.service.ReviewService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/review")
public class ReviewController {

    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @RequireRole({"ADMIN", "REVIEWER"})
    @GetMapping("/pending-waves")
    public ApiResult<List<Map<String, Object>>> pendingWaves() {
        return ApiResult.ok(reviewService.pendingWaves());
    }

    @RequireRole({"ADMIN", "REVIEWER"})
    @PostMapping("/claim")
    public ApiResult<List<Map<String, Object>>> claim(@RequestParam String waveNo) {
        return ApiResult.ok(reviewService.claim(waveNo));
    }

    @RequireRole({"ADMIN", "REVIEWER"})
    @PostMapping("/submit")
    public ApiResult<Map<String, Object>> submit(@RequestBody Map<String, Object> body) {
        return ApiResult.ok(reviewService.submit(body));
    }

    @GetMapping("/page")
    public ApiResult<PageResult<Map<String, Object>>> page(
            @RequestParam(required = false) String reviewNo,
            @RequestParam(required = false) String waveNo,
            @RequestParam(required = false) String orderNo,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String diffType,
            @RequestParam(required = false) String reviewerNo,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        return ApiResult.ok(reviewService.page(reviewNo, waveNo, orderNo, status, diffType, reviewerNo, pageNum, pageSize));
    }

    @GetMapping("/{reviewNo}")
    public ApiResult<Map<String, Object>> detail(@PathVariable String reviewNo) {
        return ApiResult.ok(reviewService.detail(reviewNo));
    }
}
