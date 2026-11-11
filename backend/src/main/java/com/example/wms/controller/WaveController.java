package com.example.wms.controller;

import com.example.wms.common.RequireRole;
import com.example.wms.dto.ApiResult;
import com.example.wms.dto.PageResult;
import com.example.wms.service.WaveService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/wave")
public class WaveController {

    private final WaveService waveService;

    public WaveController(WaveService waveService) {
        this.waveService = waveService;
    }

    @GetMapping("/picker-workload")
    public ApiResult<List<Map<String, Object>>> pickerWorkload() {
        return ApiResult.ok(waveService.pickerWorkload());
    }

    @RequireRole({"ADMIN", "WAVE_OPERATOR"})
    @PostMapping("/preview")
    public ApiResult<Map<String, Object>> preview(@RequestBody(required = false) Map<String, Object> body) {
        return ApiResult.ok(waveService.preview(body));
    }

    @RequireRole({"ADMIN", "WAVE_OPERATOR"})
    @PostMapping("/generate")
    public ApiResult<List<Map<String, Object>>> generate(@RequestBody(required = false) Map<String, Object> body) {
        return ApiResult.ok(waveService.generate(body));
    }

    @RequireRole({"ADMIN", "WAVE_OPERATOR"})
    @PostMapping("/assign")
    public ApiResult<Map<String, Object>> assign(@RequestBody Map<String, Object> body) {
        return ApiResult.ok(waveService.assign(body));
    }

    @RequireRole({"ADMIN", "WAVE_OPERATOR"})
    @PostMapping("/{waveNo}/close")
    public ApiResult<Map<String, Object>> close(@PathVariable String waveNo) {
        return ApiResult.ok(waveService.close(waveNo));
    }

    @RequireRole({"ADMIN", "WAVE_OPERATOR"})
    @PostMapping("/{waveNo}/cancel")
    public ApiResult<Map<String, Object>> cancel(@PathVariable String waveNo) {
        return ApiResult.ok(waveService.cancel(waveNo));
    }

    @GetMapping("/page")
    public ApiResult<PageResult<Map<String, Object>>> page(
            @RequestParam(required = false) String waveNo,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String strategy,
            @RequestParam(required = false) String pickerNo,
            @RequestParam(required = false) Boolean onlyWorking,
            @RequestParam(required = false) Boolean onlyShortage,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        return ApiResult.ok(waveService.page(waveNo, status, strategy, pickerNo, onlyWorking, onlyShortage, pageNum, pageSize));
    }

    @GetMapping("/{waveNo}")
    public ApiResult<Map<String, Object>> detail(@PathVariable String waveNo) {
        return ApiResult.ok(waveService.detail(waveNo));
    }
}
