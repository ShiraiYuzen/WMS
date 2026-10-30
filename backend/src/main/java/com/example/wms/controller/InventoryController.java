package com.example.wms.controller;

import com.example.wms.dto.ApiResult;
import com.example.wms.dto.PageResult;
import com.example.wms.entity.Inventory;
import com.example.wms.service.InventoryService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/inventory")
public class InventoryController {

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @GetMapping("/list")
    public ApiResult<PageResult<Inventory>> list(@RequestParam(required = false) String keyword,
                                                 @RequestParam(defaultValue = "1") int page,
                                                 @RequestParam(defaultValue = "10") int size) {
        return ApiResult.ok(inventoryService.list(keyword, page, size));
    }
}
