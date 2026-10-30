package com.example.wms.controller;

import com.example.wms.dto.ApiResult;
import com.example.wms.dto.ReviewSubmitRequest;
import com.example.wms.service.ReviewService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/review")
public class ReviewController {

    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @GetMapping("/detail/{waveId}")
    public ApiResult<Map<String, Object>> detail(@PathVariable Long waveId) {
        return ApiResult.ok(reviewService.detail(waveId));
    }

    @PostMapping("/submit")
    public ApiResult<Void> submit(@RequestBody ReviewSubmitRequest req, @RequestAttribute("loginUser") String username) {
        reviewService.submit(req, username);
        return ApiResult.ok();
    }
}
