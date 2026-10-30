package com.example.wms.controller;

import com.example.wms.dto.ApiResult;
import com.example.wms.dto.PickSubmitRequest;
import com.example.wms.service.PickService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/pick")
public class PickController {

    private final PickService pickService;

    public PickController(PickService pickService) {
        this.pickService = pickService;
    }

    @GetMapping("/detail/{waveId}")
    public ApiResult<Map<String, Object>> detail(@PathVariable Long waveId) {
        return ApiResult.ok(pickService.detail(waveId));
    }

    @PostMapping("/start")
    public ApiResult<Void> start(@RequestBody Map<String, Object> body) {
        Object id = body.get("waveId");
        pickService.start(id == null ? null : Long.valueOf(id.toString()));
        return ApiResult.ok();
    }

    @PostMapping("/submit")
    public ApiResult<Void> submit(@RequestBody PickSubmitRequest req, @RequestAttribute("loginUser") String username) {
        pickService.submit(req, username);
        return ApiResult.ok();
    }

    @PostMapping("/finish")
    public ApiResult<Void> finish(@RequestBody Map<String, Object> body) {
        Object id = body.get("waveId");
        pickService.finish(id == null ? null : Long.valueOf(id.toString()));
        return ApiResult.ok();
    }
}
