package com.example.wms.controller;

import com.example.wms.dao.ErrorDao;
import com.example.wms.dto.ApiResult;
import com.example.wms.dto.PageResult;
import com.example.wms.entity.ErrorRecord;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/error")
public class ErrorController {

    private final ErrorDao errorDao;

    public ErrorController(ErrorDao errorDao) {
        this.errorDao = errorDao;
    }

    @GetMapping("/list")
    public ApiResult<PageResult<ErrorRecord>> list(@RequestParam(required = false) String errorType,
                                                   @RequestParam(required = false) String keyword,
                                                   @RequestParam(defaultValue = "1") int page,
                                                   @RequestParam(defaultValue = "10") int size) {
        int p = Math.max(page, 1);
        int s = Math.min(Math.max(size, 1), 200);
        long total = errorDao.count(errorType, keyword);
        return ApiResult.ok(new PageResult<>(errorDao.findPage(errorType, keyword, (p - 1) * s, s), total));
    }
}
