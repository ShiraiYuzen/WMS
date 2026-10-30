package com.example.wms.controller;

import com.example.wms.dto.ApiResult;
import com.example.wms.dto.OutboundSubmitRequest;
import com.example.wms.dto.PageResult;
import com.example.wms.entity.Outbound;
import com.example.wms.service.OutboundService;
import com.example.wms.dao.OutboundDao;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/outbound")
public class OutboundController {

    private final OutboundService outboundService;
    private final OutboundDao outboundDao;

    public OutboundController(OutboundService outboundService, OutboundDao outboundDao) {
        this.outboundService = outboundService;
        this.outboundDao = outboundDao;
    }

    @GetMapping("/list")
    public ApiResult<PageResult<Outbound>> list(@RequestParam(required = false) String keyword,
                                                @RequestParam(defaultValue = "1") int page,
                                                @RequestParam(defaultValue = "10") int size) {
        int p = Math.max(page, 1);
        int s = Math.min(Math.max(size, 1), 200);
        long total = outboundDao.count(keyword);
        return ApiResult.ok(new PageResult<>(outboundDao.findPage(keyword, (p - 1) * s, s), total));
    }

    @PostMapping("/submit")
    public ApiResult<Outbound> submit(@RequestBody OutboundSubmitRequest req, @RequestAttribute("loginUser") String username) {
        return ApiResult.ok(outboundService.submit(req, username));
    }
}
