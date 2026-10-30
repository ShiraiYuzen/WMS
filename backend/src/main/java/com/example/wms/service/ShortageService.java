package com.example.wms.service;

import com.example.wms.dao.ShortageDao;
import com.example.wms.dto.PageResult;
import com.example.wms.entity.Shortage;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ShortageService {

    private final ShortageDao shortageDao;

    public ShortageService(ShortageDao shortageDao) {
        this.shortageDao = shortageDao;
    }

    public PageResult<Shortage> list(String status, String keyword, int page, int size) {
        int p = Math.max(page, 1);
        int s = Math.min(Math.max(size, 1), 200);
        long total = shortageDao.count(status, keyword);
        List<Shortage> list = shortageDao.findPage(status, keyword, (p - 1) * s, s);
        return new PageResult<>(list, total);
    }

    public void handle(Long id, String status, String remark) {
        if (id == null) {
            throw new IllegalArgumentException("缺少缺货记录 ID");
        }
        if (status == null || status.isBlank()) {
            status = "已补货";
        }
        shortageDao.handle(id, status, remark);
    }
}
