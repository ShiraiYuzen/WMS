package com.example.wms.controller;

import com.example.wms.dto.ApiResult;
import com.example.wms.dto.PageResult;
import com.example.wms.entity.Sku;
import com.example.wms.service.SkuService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/sku")
public class SkuController {

    private final SkuService skuService;

    public SkuController(SkuService skuService) {
        this.skuService = skuService;
    }

    @GetMapping("/list")
    public ApiResult<PageResult<Sku>> list(@RequestParam(required = false) String keyword,
                                           @RequestParam(required = false) String category,
                                           @RequestParam(defaultValue = "1") int page,
                                           @RequestParam(defaultValue = "10") int size) {
        return ApiResult.ok(skuService.list(keyword, category, page, size));
    }

    @GetMapping("/all")
    public ApiResult<List<Sku>> all() {
        return ApiResult.ok(skuService.listAll());
    }

    @PostMapping("/add")
    public ApiResult<Sku> add(@RequestBody Sku sku) {
        return ApiResult.ok(skuService.add(sku));
    }

    @PostMapping("/update")
    public ApiResult<Void> update(@RequestBody Sku sku) {
        skuService.update(sku);
        return ApiResult.ok();
    }

    @PostMapping("/delete")
    public ApiResult<Void> delete(@RequestBody Map<String, Object> body) {
        Object id = body.get("id");
        skuService.delete(id == null ? null : Long.valueOf(id.toString()));
        return ApiResult.ok();
    }
}
