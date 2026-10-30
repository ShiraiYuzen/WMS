package com.example.wms.controller;

import com.example.wms.dto.ApiResult;
import com.example.wms.dto.PageResult;
import com.example.wms.entity.Carrier;
import com.example.wms.entity.WarehouseLocation;
import com.example.wms.service.BaseDataService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** 基础数据接口：库位 / 承运商（第三版新增） */
@RestController
@RequestMapping("/api/base")
public class BaseDataController {

    private final BaseDataService baseDataService;

    public BaseDataController(BaseDataService baseDataService) {
        this.baseDataService = baseDataService;
    }

    // ---------------- 库位 ----------------

    @GetMapping("/location/page")
    public ApiResult<PageResult<WarehouseLocation>> locationPage(@RequestParam(required = false) String zone,
                                                                 @RequestParam(required = false) String keyword,
                                                                 @RequestParam(defaultValue = "1") int page,
                                                                 @RequestParam(defaultValue = "10") int size) {
        return ApiResult.ok(baseDataService.locationPage(zone, keyword, page, size));
    }

    @GetMapping("/location/all")
    public ApiResult<List<WarehouseLocation>> locationAll() {
        return ApiResult.ok(baseDataService.locationAll());
    }

    @GetMapping("/location/zones")
    public ApiResult<List<String>> zones() {
        return ApiResult.ok(baseDataService.zones());
    }

    @PostMapping("/location/save")
    public ApiResult<WarehouseLocation> locationSave(@RequestBody WarehouseLocation l) {
        return ApiResult.ok(baseDataService.locationSave(l));
    }

    @PostMapping("/location/delete")
    public ApiResult<Void> locationDelete(@RequestBody Map<String, Object> body) {
        Object id = body.get("id");
        baseDataService.locationDelete(id == null ? null : Long.valueOf(id.toString()));
        return ApiResult.ok();
    }

    // ---------------- 承运商 ----------------

    @GetMapping("/carrier/page")
    public ApiResult<PageResult<Carrier>> carrierPage(@RequestParam(required = false) String keyword,
                                                      @RequestParam(defaultValue = "1") int page,
                                                      @RequestParam(defaultValue = "10") int size) {
        return ApiResult.ok(baseDataService.carrierPage(keyword, page, size));
    }

    @GetMapping("/carrier/list")
    public ApiResult<List<Carrier>> carrierList() {
        return ApiResult.ok(baseDataService.carrierEnabled());
    }

    @PostMapping("/carrier/save")
    public ApiResult<Carrier> carrierSave(@RequestBody Carrier c) {
        return ApiResult.ok(baseDataService.carrierSave(c));
    }

    @PostMapping("/carrier/delete")
    public ApiResult<Void> carrierDelete(@RequestBody Map<String, Object> body) {
        Object id = body.get("id");
        baseDataService.carrierDelete(id == null ? null : Long.valueOf(id.toString()));
        return ApiResult.ok();
    }
}
