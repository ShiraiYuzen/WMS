package com.example.wms.service;

import com.example.wms.dao.CarrierDao;
import com.example.wms.dao.LocationDao;
import com.example.wms.dto.PageResult;
import com.example.wms.entity.Carrier;
import com.example.wms.entity.WarehouseLocation;
import org.springframework.stereotype.Service;

import java.util.List;

/** 基础数据服务：库位 + 承运商（第三版新增） */
@Service
public class BaseDataService {

    private final LocationDao locationDao;
    private final CarrierDao carrierDao;

    public BaseDataService(LocationDao locationDao, CarrierDao carrierDao) {
        this.locationDao = locationDao;
        this.carrierDao = carrierDao;
    }

    // ---------------- 库位 ----------------

    public PageResult<WarehouseLocation> locationPage(String zone, String keyword, int page, int size) {
        int p = Math.max(page, 1);
        int s = Math.min(Math.max(size, 1), 200);
        long total = locationDao.count(zone, keyword);
        return new PageResult<>(locationDao.findPage(zone, keyword, (p - 1) * s, s), total);
    }

    public List<WarehouseLocation> locationAll() {
        return locationDao.findAll();
    }

    public List<String> zones() {
        return locationDao.findZones();
    }

    public WarehouseLocation locationSave(WarehouseLocation l) {
        if (l.getLocationCode() == null || l.getLocationCode().isBlank()) {
            throw new IllegalArgumentException("库位编码不能为空");
        }
        if (l.getId() == null) {
            locationDao.insert(l);
        } else {
            locationDao.update(l);
        }
        return l;
    }

    public void locationDelete(Long id) {
        locationDao.delete(id);
    }

    // ---------------- 承运商 ----------------

    public PageResult<Carrier> carrierPage(String keyword, int page, int size) {
        int p = Math.max(page, 1);
        int s = Math.min(Math.max(size, 1), 200);
        long total = carrierDao.count(keyword);
        return new PageResult<>(carrierDao.findPage(keyword, (p - 1) * s, s), total);
    }

    public List<Carrier> carrierEnabled() {
        return carrierDao.findAllEnabled();
    }

    public Carrier carrierSave(Carrier c) {
        if (c.getCarrierName() == null || c.getCarrierName().isBlank()) {
            throw new IllegalArgumentException("承运商名称不能为空");
        }
        if (c.getId() == null) {
            carrierDao.insert(c);
        } else {
            carrierDao.update(c);
        }
        return c;
    }

    public void carrierDelete(Long id) {
        carrierDao.delete(id);
    }
}
