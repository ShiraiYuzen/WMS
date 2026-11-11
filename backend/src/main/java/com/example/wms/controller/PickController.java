package com.example.wms.controller;

import com.example.wms.common.RequireRole;
import com.example.wms.dto.ApiResult;
import com.example.wms.dto.PageResult;
import com.example.wms.service.PickService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/pick")
public class PickController {

    private final PickService pickService;

    public PickController(PickService pickService) {
        this.pickService = pickService;
    }

    @GetMapping("/my-tasks")
    public ApiResult<List<Map<String, Object>>> myTasks(@RequestParam(required = false) String pickerNo) {
        return ApiResult.ok(pickService.myTasks(pickerNo));
    }

    @RequireRole({"ADMIN", "PICKER", "WAVE_OPERATOR"})
    @PostMapping("/confirm-item")
    public ApiResult<Map<String, Object>> confirmItem(@RequestBody Map<String, Object> body) {
        return ApiResult.ok(pickService.confirmItem(body));
    }

    @RequireRole({"ADMIN", "PICKER", "WAVE_OPERATOR"})
    @PostMapping("/report-shortage")
    public ApiResult<Map<String, Object>> reportShortage(@RequestBody Map<String, Object> body) {
        return ApiResult.ok(pickService.reportShortage(body));
    }

    @RequireRole({"ADMIN", "PICKER", "WAVE_OPERATOR"})
    @PostMapping("/{taskNo}/start")
    public ApiResult<Map<String, Object>> start(@PathVariable String taskNo) {
        return ApiResult.ok(pickService.start(taskNo));
    }

    @RequireRole({"ADMIN", "PICKER", "WAVE_OPERATOR"})
    @PostMapping("/{taskNo}/finish")
    public ApiResult<Map<String, Object>> finish(@PathVariable String taskNo) {
        return ApiResult.ok(pickService.finish(taskNo));
    }

    @GetMapping("/page")
    public ApiResult<PageResult<Map<String, Object>>> page(
            @RequestParam(required = false) String taskNo,
            @RequestParam(required = false) String waveNo,
            @RequestParam(required = false) String pickerNo,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        return ApiResult.ok(pickService.page(taskNo, waveNo, pickerNo, status, pageNum, pageSize));
    }

    @GetMapping("/{taskNo}")
    public ApiResult<Map<String, Object>> detail(@PathVariable String taskNo) {
        return ApiResult.ok(pickService.detail(taskNo));
    }
}
