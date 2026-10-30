package com.example.wms.controller;

import com.example.wms.dto.ApiResult;
import com.example.wms.dto.PageResult;
import com.example.wms.dto.WaveCreateRequest;
import com.example.wms.entity.Wave;
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

    @GetMapping("/list")
    public ApiResult<PageResult<Wave>> list(@RequestParam(required = false) String status,
                                            @RequestParam(required = false) String keyword,
                                            @RequestParam(defaultValue = "1") int page,
                                            @RequestParam(defaultValue = "10") int size) {
        return ApiResult.ok(waveService.list(status, keyword, page, size));
    }

    @GetMapping("/detail/{id}")
    public ApiResult<Map<String, Object>> detail(@PathVariable Long id) {
        return ApiResult.ok(waveService.detail(id));
    }

    @GetMapping("/pickable")
    public ApiResult<List<Wave>> pickable() {
        return ApiResult.ok(waveService.pickableList());
    }

    @GetMapping("/reviewable")
    public ApiResult<List<Wave>> reviewable() {
        return ApiResult.ok(waveService.reviewableList());
    }

    @GetMapping("/outboundable")
    public ApiResult<List<Wave>> outboundable() {
        return ApiResult.ok(waveService.outboundableList());
    }

    @PostMapping("/create")
    public ApiResult<Wave> create(@RequestBody WaveCreateRequest req, @RequestAttribute("loginUser") String username) {
        return ApiResult.ok(waveService.create(req, username));
    }

    /** 波次预览（第三版新增） */
    @PostMapping("/preview")
    public ApiResult<Map<String, Object>> preview(@RequestBody WaveCreateRequest req) {
        return ApiResult.ok(waveService.preview(req));
    }

    /** 拣货员工作量（第三版新增） */
    @GetMapping("/picker-workload")
    public ApiResult<List<Map<String, Object>>> pickerWorkload() {
        return ApiResult.ok(waveService.pickerWorkload());
    }

    @PostMapping("/assign")
    public ApiResult<Void> assign(@RequestBody Map<String, Object> body) {
        Object waveId = body.get("waveId");
        Object pickerId = body.get("pickerId");
        waveService.assignPicker(waveId == null ? null : Long.valueOf(waveId.toString()),
                pickerId == null ? null : Long.valueOf(pickerId.toString()));
        return ApiResult.ok();
    }

    @PostMapping("/cancel")
    public ApiResult<Void> cancel(@RequestBody Map<String, Object> body) {
        Object id = body.get("id");
        waveService.cancel(id == null ? null : Long.valueOf(id.toString()));
        return ApiResult.ok();
    }
}
