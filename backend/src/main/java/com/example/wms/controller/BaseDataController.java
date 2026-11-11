package com.example.wms.controller;

import com.example.wms.common.RequireRole;
import com.example.wms.dto.ApiResult;
import com.example.wms.dto.PageResult;
import com.example.wms.service.BaseDataService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/base")
public class BaseDataController {

    private final BaseDataService baseDataService;

    public BaseDataController(BaseDataService baseDataService) {
        this.baseDataService = baseDataService;
    }

    @GetMapping("/enums")
    public ApiResult<Map<String, List<Map<String, String>>>> enums() {
        return ApiResult.ok(baseDataService.enums());
    }

    @GetMapping("/sku/categories")
    public ApiResult<List<String>> skuCategories() {
        return ApiResult.ok(baseDataService.skuCategories());
    }

    @GetMapping("/sku/page")
    public ApiResult<PageResult<Map<String, Object>>> skuPage(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        return ApiResult.ok(baseDataService.skuPage(keyword, category, pageNum, pageSize));
    }

    @GetMapping("/location/zones")
    public ApiResult<List<String>> zones() {
        return ApiResult.ok(baseDataService.zones());
    }

    @GetMapping("/location/page")
    public ApiResult<PageResult<Map<String, Object>>> locationPage(
            @RequestParam(required = false) String zone,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        return ApiResult.ok(baseDataService.locationPage(zone, pageNum, pageSize));
    }

    @GetMapping("/employee/by-role")
    public ApiResult<List<Map<String, Object>>> employeeByRole(@RequestParam(required = false) String role) {
        return ApiResult.ok(baseDataService.employeeByRole(role));
    }

    @RequireRole({"ADMIN"})
    @GetMapping("/employee/teams")
    public ApiResult<List<String>> teams() {
        return ApiResult.ok(baseDataService.teams());
    }

    @RequireRole({"ADMIN"})
    @GetMapping("/employee/page")
    public ApiResult<PageResult<Map<String, Object>>> employeePage(
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String team,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        return ApiResult.ok(baseDataService.employeePage(role, team, pageNum, pageSize));
    }

    @GetMapping("/carrier/list")
    public ApiResult<List<Map<String, Object>>> carrierList() {
        return ApiResult.ok(baseDataService.carrierList());
    }
}
