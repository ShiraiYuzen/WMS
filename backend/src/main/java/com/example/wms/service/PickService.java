package com.example.wms.service;

import com.example.wms.dao.OrderDao;
import com.example.wms.dao.OrderItemDao;
import com.example.wms.dao.ShortageDao;
import com.example.wms.dao.UserDao;
import com.example.wms.dao.WaveDao;
import com.example.wms.dao.WaveItemDao;
import com.example.wms.dto.PickSubmitRequest;
import com.example.wms.entity.Order;
import com.example.wms.entity.Shortage;
import com.example.wms.entity.User;
import com.example.wms.entity.Wave;
import com.example.wms.entity.WaveItem;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class PickService {

    private final WaveDao waveDao;
    private final WaveItemDao waveItemDao;
    private final OrderDao orderDao;
    private final OrderItemDao orderItemDao;
    private final ShortageDao shortageDao;
    private final UserDao userDao;

    public PickService(WaveDao waveDao, WaveItemDao waveItemDao, OrderDao orderDao, OrderItemDao orderItemDao,
                       ShortageDao shortageDao, UserDao userDao) {
        this.waveDao = waveDao;
        this.waveItemDao = waveItemDao;
        this.orderDao = orderDao;
        this.orderItemDao = orderItemDao;
        this.shortageDao = shortageDao;
        this.userDao = userDao;
    }

    public Map<String, Object> detail(Long waveId) {
        Wave wave = waveDao.findById(waveId);
        if (wave == null) {
            throw new IllegalArgumentException("波次不存在");
        }
        Map<String, Object> map = new HashMap<>();
        map.put("wave", wave);
        map.put("items", waveItemDao.findByWaveId(waveId));
        map.put("orders", orderDao.findByWaveId(waveId));
        map.put("shortages", shortageDao.findByWaveId(waveId));
        return map;
    }

    @Transactional
    public void start(Long waveId) {
        Wave wave = waveDao.findById(waveId);
        if (wave == null) {
            throw new IllegalArgumentException("波次不存在");
        }
        if (!"待拣货".equals(wave.getStatus())) {
            throw new IllegalArgumentException("仅「待拣货」波次可开始拣货，当前状态：" + wave.getStatus());
        }
        waveDao.markStart(waveId);
        List<Order> orders = orderDao.findByWaveId(waveId);
        orderDao.updateStatusByIds(orders.stream().map(Order::getId).toList(), "拣货中");
    }

    @Transactional
    public void submit(PickSubmitRequest req, String username) {
        if (req == null || req.getWaveId() == null) {
            throw new IllegalArgumentException("缺少波次 ID");
        }
        Wave wave = waveDao.findById(req.getWaveId());
        if (wave == null) {
            throw new IllegalArgumentException("波次不存在");
        }
        if (!"拣货中".equals(wave.getStatus())) {
            throw new IllegalArgumentException("波次未处于拣货中，无法提交");
        }
        List<Order> orders = orderDao.findByWaveId(req.getWaveId());
        List<Long> orderIds = orders.stream().map(Order::getId).toList();
        User reporter = userDao.findByUsername(username);

        List<WaveItem> current = waveItemDao.findByWaveId(req.getWaveId());
        for (WaveItem wi : current) {
            PickSubmitRequest.ItemLine line = req.getItems().stream()
                    .filter(i -> wi.getSkuCode().equals(i.getSkuCode()))
                    .findFirst().orElse(null);
            if (line == null) {
                continue;
            }
            int required = wi.getRequiredQty() == null ? 0 : wi.getRequiredQty();
            int picked = line.getPickedQty() == null ? 0 : Math.max(line.getPickedQty(), 0);
            if (picked > required) {
                picked = required;
            }
            int shortage = Math.max(required - picked, 0);
            boolean full = shortage == 0;
            waveItemDao.updatePicked(req.getWaveId(), wi.getSkuCode(), picked, shortage, full ? "已拣" : "缺货");
            orderItemDao.markPickBySku(orderIds, wi.getSkuCode(), full);

            if (!full) {
                Shortage s = new Shortage();
                s.setWaveId(wave.getId());
                s.setWaveNo(wave.getWaveNo());
                s.setSkuId(wi.getSkuId());
                s.setSkuCode(wi.getSkuCode());
                s.setSkuName(wi.getSkuName());
                s.setLocation(wi.getLocation());
                s.setRequiredQty(required);
                s.setPickedQty(picked);
                s.setShortQty(shortage);
                s.setReason(line.getReason() == null || line.getReason().isBlank() ? "库存不足" : line.getReason());
                if (reporter != null) {
                    s.setReporterId(reporter.getId());
                    s.setReporterName(reporter.getNickname());
                }
                shortageDao.insert(s);
            }
        }
    }

    @Transactional
    public void finish(Long waveId) {
        Wave wave = waveDao.findById(waveId);
        if (wave == null) {
            throw new IllegalArgumentException("波次不存在");
        }
        if (!"拣货中".equals(wave.getStatus())) {
            throw new IllegalArgumentException("仅「拣货中」波次可完成拣货，当前状态：" + wave.getStatus());
        }
        long unfinished = waveItemDao.countUnfinished(waveId);
        if (unfinished > 0) {
            throw new IllegalArgumentException("还有 " + unfinished + " 个明细未拣货，请先提交拣货数量");
        }
        waveDao.markPickFinish(waveId);
        List<Order> orders = orderDao.findByWaveId(waveId);
        orderDao.updateStatusByIds(orders.stream().map(Order::getId).toList(), "已拣货");
    }
}
