package com.example.wms.service;

import com.example.wms.dao.InventoryDao;
import com.example.wms.dao.OrderDao;
import com.example.wms.dao.OrderItemDao;
import com.example.wms.dao.OutboundDao;
import com.example.wms.dao.ReviewDao;
import com.example.wms.dao.ShortageDao;
import com.example.wms.dao.UserDao;
import com.example.wms.dao.WaveDao;
import com.example.wms.dao.WaveItemDao;
import com.example.wms.dto.PageResult;
import com.example.wms.dto.WaveCreateRequest;
import com.example.wms.entity.Inventory;
import com.example.wms.entity.Order;
import com.example.wms.entity.User;
import com.example.wms.entity.Wave;
import com.example.wms.entity.WaveItem;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class WaveService {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final WaveDao waveDao;
    private final WaveItemDao waveItemDao;
    private final OrderDao orderDao;
    private final OrderItemDao orderItemDao;
    private final InventoryDao inventoryDao;
    private final UserDao userDao;
    private final ShortageDao shortageDao;
    private final ReviewDao reviewDao;
    private final OutboundDao outboundDao;

    public WaveService(WaveDao waveDao, WaveItemDao waveItemDao, OrderDao orderDao, OrderItemDao orderItemDao,
                       InventoryDao inventoryDao, UserDao userDao, ShortageDao shortageDao,
                       ReviewDao reviewDao, OutboundDao outboundDao) {
        this.waveDao = waveDao;
        this.waveItemDao = waveItemDao;
        this.orderDao = orderDao;
        this.orderItemDao = orderItemDao;
        this.inventoryDao = inventoryDao;
        this.userDao = userDao;
        this.shortageDao = shortageDao;
        this.reviewDao = reviewDao;
        this.outboundDao = outboundDao;
    }

    public PageResult<Wave> list(String status, String keyword, int page, int size) {
        int p = Math.max(page, 1);
        int s = Math.min(Math.max(size, 1), 200);
        long total = waveDao.count(status, keyword);
        List<Wave> list = waveDao.findPage(status, keyword, (p - 1) * s, s);
        return new PageResult<>(list, total);
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
        map.put("reviews", reviewDao.findByWaveId(waveId));
        map.put("outbound", outboundDao.findByWaveId(waveId));
        return map;
    }

    /** 待拣货 / 拣货中 的波次（拣货工作台用） */
    public List<Wave> pickableList() {
        List<Wave> list = new ArrayList<>();
        list.addAll(waveDao.findByStatus("待拣货"));
        list.addAll(waveDao.findByStatus("拣货中"));
        return list;
    }

    /** 待复核波次 */
    public List<Wave> reviewableList() {
        return waveDao.findByStatus("待复核");
    }

    /** 待出库波次 */
    public List<Wave> outboundableList() {
        return waveDao.findByStatus("待出库");
    }

    @Transactional
    public Wave create(WaveCreateRequest req, String username) {
        if (req == null || req.getOrderIds() == null || req.getOrderIds().isEmpty()) {
            throw new IllegalArgumentException("请至少选择一个待分波订单");
        }
        List<Order> orders = orderDao.findByIds(req.getOrderIds());
        if (orders.size() != req.getOrderIds().size()) {
            throw new IllegalArgumentException("部分订单不存在，请刷新后重试");
        }
        for (Order o : orders) {
            if (!"待分波".equals(o.getStatus())) {
                throw new IllegalArgumentException("订单 " + o.getOrderNo() + " 当前状态为「" + o.getStatus() + "」，不可分波");
            }
        }

        // 按 SKU 聚合拣货清单
        List<WaveItem> aggregated = orderItemDao.aggregateBySku(req.getOrderIds());
        if (aggregated.isEmpty()) {
            throw new IllegalArgumentException("所选订单没有有效明细");
        }

        // 库存校验（可用库存需满足拣货需求）
        StringBuilder insufficient = new StringBuilder();
        for (WaveItem wi : aggregated) {
            Inventory inv = inventoryDao.findBySkuId(wi.getSkuId());
            int available = inv == null ? 0 : (inv.getAvailableQty() == null ? 0 : inv.getAvailableQty());
            if (available < wi.getRequiredQty()) {
                insufficient.append("[").append(wi.getSkuCode()).append(" 需求").append(wi.getRequiredQty())
                        .append("/可用").append(available).append("] ");
            }
        }
        if (insufficient.length() > 0) {
            throw new IllegalArgumentException("库存不足，无法生成波次：" + insufficient);
        }

        User creator = userDao.findByUsername(username);
        User picker = req.getPickerId() == null ? null : userDao.findById(req.getPickerId());

        int totalQty = aggregated.stream().mapToInt(WaveItem::getRequiredQty).sum();
        Wave wave = new Wave();
        wave.setWaveNo(nextWaveNo());
        wave.setStatus("待拣货");
        wave.setOrderCount(orders.size());
        wave.setItemCount(aggregated.size());
        wave.setTotalQty(totalQty);
        wave.setStrategy(req.getStrategy() == null || req.getStrategy().isBlank() ? "按库位聚合" : req.getStrategy());
        wave.setRemark(req.getRemark());
        if (picker != null) {
            wave.setPickerId(picker.getId());
            wave.setPickerName(picker.getNickname());
        }
        if (creator != null) {
            wave.setCreatorId(creator.getId());
            wave.setCreatorName(creator.getNickname());
        }
        long waveId = waveDao.insert(wave);

        for (WaveItem wi : aggregated) {
            wi.setWaveId(waveId);
            waveItemDao.insert(wi);
            inventoryDao.lock(wi.getSkuId(), wi.getRequiredQty());
        }

        orderDao.assignWave(req.getOrderIds(), waveId, wave.getWaveNo());
        return waveDao.findById(waveId);
    }

    public void assignPicker(Long waveId, Long pickerId) {
        Wave wave = waveDao.findById(waveId);
        if (wave == null) {
            throw new IllegalArgumentException("波次不存在");
        }
        User picker = userDao.findById(pickerId);
        if (picker == null) {
            throw new IllegalArgumentException("拣货员不存在");
        }
        waveDao.assignPicker(waveId, picker.getId(), picker.getNickname());
    }

    @Transactional
    public void cancel(Long waveId) {
        Wave wave = waveDao.findById(waveId);
        if (wave == null) {
            throw new IllegalArgumentException("波次不存在");
        }
        if (!"待拣货".equals(wave.getStatus())) {
            throw new IllegalArgumentException("仅「待拣货」波次可取消，当前状态：" + wave.getStatus());
        }
        List<WaveItem> items = waveItemDao.findByWaveId(waveId);
        for (WaveItem wi : items) {
            inventoryDao.unlock(wi.getSkuId(), wi.getRequiredQty());
        }
        waveItemDao.deleteByWaveId(waveId);
        List<Order> orders = orderDao.findByWaveId(waveId);
        List<Long> ids = orders.stream().map(Order::getId).toList();
        if (!ids.isEmpty()) {
            orderDao.clearWave(ids);
        }
        waveDao.updateStatus(waveId, "已取消");
    }

    private String nextWaveNo() {
        long seq = waveDao.countTodayWaves() + 1;
        return "W" + LocalDate.now().format(DAY) + String.format("%03d", seq);
    }

    // ===================== 第三版新增（融合自 A 版） =====================

    /** 波次预览：不落库，返回按策略排序的聚合拣货清单 + 库存校验结果 */
    public Map<String, Object> preview(WaveCreateRequest req) {
        if (req == null || req.getOrderIds() == null || req.getOrderIds().isEmpty()) {
            throw new IllegalArgumentException("请至少选择一个待分波订单");
        }
        List<WaveItem> aggregated = orderItemDao.aggregateBySku(req.getOrderIds());
        String strategy = req.getStrategy() == null || req.getStrategy().isBlank() ? "按库位聚合" : req.getStrategy();
        sortByStrategy(aggregated, strategy);

        List<Map<String, Object>> items = new ArrayList<>();
        List<String> insufficient = new ArrayList<>();
        int totalQty = 0;
        for (WaveItem wi : aggregated) {
            int need = wi.getRequiredQty() == null ? 0 : wi.getRequiredQty();
            totalQty += need;
            Inventory inv = inventoryDao.findBySkuId(wi.getSkuId());
            int available = inv == null ? 0 : (inv.getAvailableQty() == null ? 0 : inv.getAvailableQty());
            boolean enough = available >= need;
            if (!enough) {
                insufficient.add(wi.getSkuCode() + " 需求" + need + "/可用" + available);
            }
            Map<String, Object> row = new HashMap<>();
            row.put("skuCode", wi.getSkuCode());
            row.put("skuName", wi.getSkuName());
            row.put("location", wi.getLocation());
            row.put("requiredQty", need);
            row.put("availableQty", available);
            row.put("enough", enough);
            items.add(row);
        }
        Map<String, Object> map = new HashMap<>();
        map.put("strategy", strategy);
        map.put("orderCount", req.getOrderIds().size());
        map.put("itemCount", aggregated.size());
        map.put("totalQty", totalQty);
        map.put("items", items);
        map.put("insufficient", insufficient);
        map.put("pass", insufficient.isEmpty());
        return map;
    }

    /** 按策略排序拣货清单：按库位/承运商聚合时按库位排序，减少走位 */
    private void sortByStrategy(List<WaveItem> items, String strategy) {
        if ("按时效优先".equals(strategy)) {
            return;
        }
        items.sort((a, b) -> {
            String la = a.getLocation() == null ? "" : a.getLocation();
            String lb = b.getLocation() == null ? "" : b.getLocation();
            return la.compareTo(lb);
        });
    }

    /** 拣货员工作量：各状态波次数（第三版新增） */
    public List<Map<String, Object>> pickerWorkload() {
        List<User> pickers = userDao.findByRole("PICKER");
        List<Map<String, Object>> list = new ArrayList<>();
        for (User u : pickers) {
            Map<String, Object> row = new HashMap<>();
            row.put("pickerId", u.getId());
            row.put("pickerName", u.getNickname());
            row.put("todo", waveDao.countByPickerAndStatus(u.getId(), "待拣货"));
            row.put("picking", waveDao.countByPickerAndStatus(u.getId(), "拣货中"));
            row.put("done", waveDao.countByPickerAndStatus(u.getId(), "待复核")
                    + waveDao.countByPickerAndStatus(u.getId(), "待出库")
                    + waveDao.countByPickerAndStatus(u.getId(), "已出库"));
            list.add(row);
        }
        return list;
    }
}
