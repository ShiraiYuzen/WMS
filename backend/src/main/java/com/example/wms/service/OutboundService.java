package com.example.wms.service;

import com.example.wms.dao.InventoryDao;
import com.example.wms.dao.OrderDao;
import com.example.wms.dao.OutboundDao;
import com.example.wms.dao.UserDao;
import com.example.wms.dao.WaveDao;
import com.example.wms.dao.WaveItemDao;
import com.example.wms.dto.OutboundSubmitRequest;
import com.example.wms.entity.Order;
import com.example.wms.entity.Outbound;
import com.example.wms.entity.User;
import com.example.wms.entity.Wave;
import com.example.wms.entity.WaveItem;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class OutboundService {

    private final WaveDao waveDao;
    private final WaveItemDao waveItemDao;
    private final OrderDao orderDao;
    private final OutboundDao outboundDao;
    private final InventoryDao inventoryDao;
    private final UserDao userDao;

    public OutboundService(WaveDao waveDao, WaveItemDao waveItemDao, OrderDao orderDao, OutboundDao outboundDao,
                           InventoryDao inventoryDao, UserDao userDao) {
        this.waveDao = waveDao;
        this.waveItemDao = waveItemDao;
        this.orderDao = orderDao;
        this.outboundDao = outboundDao;
        this.inventoryDao = inventoryDao;
        this.userDao = userDao;
    }

    @Transactional
    public Outbound submit(OutboundSubmitRequest req, String username) {
        if (req == null || req.getWaveId() == null) {
            throw new IllegalArgumentException("缺少波次 ID");
        }
        Wave wave = waveDao.findById(req.getWaveId());
        if (wave == null) {
            throw new IllegalArgumentException("波次不存在");
        }
        if (!"待出库".equals(wave.getStatus())) {
            throw new IllegalArgumentException("仅「待出库」波次可交接出库，当前状态：" + wave.getStatus());
        }
        if (req.getCarrier() == null || req.getCarrier().isBlank()) {
            throw new IllegalArgumentException("请填写承运商");
        }
        User operator = userDao.findByUsername(username);

        List<WaveItem> items = waveItemDao.findByWaveId(wave.getId());
        int totalQty = 0;
        for (WaveItem wi : items) {
            int picked = wi.getPickedQty() == null ? 0 : wi.getPickedQty();
            totalQty += picked;
            if (picked > 0) {
                inventoryDao.deduct(wi.getSkuId(), picked);
            }
        }
        List<Order> orders = orderDao.findByWaveId(wave.getId());

        Outbound o = new Outbound();
        o.setWaveId(wave.getId());
        o.setWaveNo(wave.getWaveNo());
        o.setOrderCount(orders.size());
        o.setTotalQty(totalQty);
        o.setCarrier(req.getCarrier());
        o.setHandoverTo(req.getHandoverTo());
        if (operator != null) {
            o.setOperatorId(operator.getId());
            o.setOperatorName(operator.getNickname());
        }
        o.setRemark(req.getRemark());
        long id = outboundDao.insert(o);

        waveDao.markOutbound(wave.getId());
        orderDao.finishByIds(orders.stream().map(Order::getId).toList(), "已出库");

        return outboundDao.findByWaveId(wave.getId()).isEmpty() ? null : outboundDao.findByWaveId(wave.getId()).get(0);
    }
}
