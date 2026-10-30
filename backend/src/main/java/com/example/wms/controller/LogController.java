package com.example.wms.controller;

import com.example.wms.dto.ApiResult;
import com.example.wms.dto.PageResult;
import com.example.wms.entity.OperationLog;
import com.example.wms.service.OperationLogService;
import org.springframework.web.bind.annotation.*;

/** 操作日志查询接口（第三版新增） */
@RestController
@RequestMapping("/api/log")
public class LogController {

    private final OperationLogService logService;

    public LogController(OperationLogService logService) {
        this.logService = logService;
    }

    @GetMapping("/page")
    public ApiResult<PageResult<OperationLog>> page(@RequestParam(required = false) String module,
                                                    @RequestParam(required = false) String keyword,
                                                    @RequestParam(defaultValue = "1") int page,
                                                    @RequestParam(defaultValue = "15") int size) {
        return ApiResult.ok(logService.list(module, keyword, page, size));
    }
}
