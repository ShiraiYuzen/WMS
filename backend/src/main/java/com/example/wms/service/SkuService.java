package com.example.wms.service;

import com.example.wms.dao.SkuDao;
import com.example.wms.dto.PageResult;
import com.example.wms.entity.Sku;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SkuService {

    private final SkuDao skuDao;

    public SkuService(SkuDao skuDao) {
        this.skuDao = skuDao;
    }

    public PageResult<Sku> list(String keyword, String category, int page, int size) {
        int p = Math.max(page, 1);
        int s = Math.min(Math.max(size, 1), 200);
        long total = skuDao.count(keyword, category);
        List<Sku> list = skuDao.findPage(keyword, category, (p - 1) * s, s);
        return new PageResult<>(list, total);
    }

    public List<Sku> listAll() {
        return skuDao.findAll();
    }

    public Sku add(Sku s) {
        if (s.getSkuCode() == null || s.getSkuCode().isBlank()) {
            throw new IllegalArgumentException("SKU 编码不能为空");
        }
        if (s.getName() == null || s.getName().isBlank()) {
            throw new IllegalArgumentException("商品名称不能为空");
        }
        if (skuDao.findByCode(s.getSkuCode().trim()) != null) {
            throw new IllegalArgumentException("SKU 编码已存在：" + s.getSkuCode());
        }
        long id = skuDao.insert(s);
        return skuDao.findById(id);
    }

    public void update(Sku s) {
        if (s.getId() == null) {
            throw new IllegalArgumentException("缺少 SKU ID");
        }
        skuDao.update(s);
    }

    public void delete(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("缺少 SKU ID");
        }
        skuDao.delete(id);
    }
}
