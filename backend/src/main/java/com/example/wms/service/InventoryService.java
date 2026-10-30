package com.example.wms.service;

import com.example.wms.dao.InventoryDao;
import com.example.wms.dto.PageResult;
import com.example.wms.entity.Inventory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class InventoryService {

    private final InventoryDao inventoryDao;

    public InventoryService(InventoryDao inventoryDao) {
        this.inventoryDao = inventoryDao;
    }

    public PageResult<Inventory> list(String keyword, int page, int size) {
        int p = Math.max(page, 1);
        int s = Math.min(Math.max(size, 1), 200);
        long total = inventoryDao.count(keyword);
        List<Inventory> list = inventoryDao.findPage(keyword, (p - 1) * s, s);
        return new PageResult<>(list, total);
    }

    public long countLowStock(int threshold) {
        return inventoryDao.countLowStock(threshold);
    }
}
