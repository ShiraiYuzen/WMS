package com.example.wms.service;

import com.example.wms.dao.OrderDao;
import com.example.wms.dao.OrderItemDao;
import com.example.wms.dao.SkuDao;
import com.example.wms.dto.OrderCreateRequest;
import com.example.wms.dto.PageResult;
import com.example.wms.entity.Order;
import com.example.wms.entity.OrderItem;
import com.example.wms.entity.Sku;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class OrderService {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final OrderDao orderDao;
    private final OrderItemDao orderItemDao;
    private final SkuDao skuDao;

    public OrderService(OrderDao orderDao, OrderItemDao orderItemDao, SkuDao skuDao) {
        this.orderDao = orderDao;
        this.orderItemDao = orderItemDao;
        this.skuDao = skuDao;
    }

    public PageResult<Order> list(String status, String platform, String keyword, int page, int size) {
        int p = Math.max(page, 1);
        int s = Math.min(Math.max(size, 1), 200);
        long total = orderDao.count(status, platform, keyword);
        List<Order> list = orderDao.findPage(status, platform, keyword, (p - 1) * s, s);
        return new PageResult<>(list, total);
    }

    public Map<String, Object> detail(Long id) {
        Order order = orderDao.findById(id);
        if (order == null) {
            throw new IllegalArgumentException("订单不存在");
        }
        Map<String, Object> map = new HashMap<>();
        map.put("order", order);
        map.put("items", orderItemDao.findByOrderId(id));
        return map;
    }

    public List<Order> pending() {
        return orderDao.findPending(500);
    }

    public Order add(OrderCreateRequest req) {
        if (req == null || req.getItems() == null || req.getItems().isEmpty()) {
            throw new IllegalArgumentException("订单明细不能为空");
        }
        Order o = new Order();
        o.setOrderNo(nextOrderNo());
        o.setPlatform(req.getPlatform() == null ? "手工录入" : req.getPlatform());
        o.setCustomerName(req.getCustomerName());
        o.setCustomerPhone(req.getCustomerPhone());
        o.setAddress(req.getAddress());
        o.setPriority(req.getPriority() == null ? "普通" : req.getPriority());
        int itemCount = 0;
        int totalQty = 0;
        for (OrderCreateRequest.ItemLine line : req.getItems()) {
            if (line.getSkuCode() == null || line.getSkuCode().isBlank()) {
                throw new IllegalArgumentException("明细中存在空的 SKU 编码");
            }
            Sku sku = skuDao.findByCode(line.getSkuCode().trim());
            if (sku == null) {
                throw new IllegalArgumentException("SKU 不存在：" + line.getSkuCode());
            }
            itemCount++;
            totalQty += line.getQty() == null ? 0 : line.getQty();
        }
        o.setItemCount(itemCount);
        o.setTotalQty(totalQty);
        long orderId = orderDao.insert(o);
        for (OrderCreateRequest.ItemLine line : req.getItems()) {
            Sku sku = skuDao.findByCode(line.getSkuCode().trim());
            OrderItem it = new OrderItem();
            it.setOrderId(orderId);
            it.setSkuId(sku.getId());
            it.setSkuCode(sku.getSkuCode());
            it.setSkuName(sku.getName());
            it.setLocation(sku.getLocation());
            it.setQty(line.getQty() == null ? 0 : line.getQty());
            orderItemDao.insert(it);
        }
        return orderDao.findById(orderId);
    }

    public void cancel(Long id) {
        Order order = orderDao.findById(id);
        if (order == null) {
            throw new IllegalArgumentException("订单不存在");
        }
        if (!"待分波".equals(order.getStatus())) {
            throw new IllegalArgumentException("仅「待分波」订单可取消，当前状态：" + order.getStatus());
        }
        orderDao.updateStatus(id, "已取消");
    }

    private String nextOrderNo() {
        long seq = orderDao.countToday() + 1;
        return "SO" + LocalDate.now().format(DAY) + String.format("%03d", seq);
    }
}
