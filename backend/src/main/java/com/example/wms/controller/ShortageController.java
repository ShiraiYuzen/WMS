package com.example.wms.controller;

import com.example.wms.dto.ApiResult;
import com.example.wms.dto.PageResult;
import com.example.wms.entity.Shortage;
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

    @GetMapping("/list")
    public ApiResult<PageResult<Shortage>> list(@RequestParam(required = false) String status,
                                                @RequestParam(required = false) String keyword,
                                                @RequestParam(defaultValue = "1") int page,
                                                @RequestParam(defaultValue = "10") int size) {
        return ApiResult.ok(shortageService.list(status, keyword, page, size));
    }

    @PostMapping("/handle")
    public ApiResult<Void> handle(@RequestBody Map<String, Object> body) {
        Object id = body.get("id");
        Object status = body.get("status");
        Object remark = body.get("remark");
        shortageService.handle(id == null ? null : Long.valueOf(id.toString()),
                status == null ? null : status.toString(),
                remark == null ? null : remark.toString());
        return ApiResult.ok();
    }
}
