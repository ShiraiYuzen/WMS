package com.example.wms.dao;

import com.example.wms.entity.OrderItem;
import com.example.wms.entity.WaveItem;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

@Repository
public class OrderItemDao {

    private final JdbcTemplate jdbc;

    public OrderItemDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final String COLS = "id, order_id, sku_id, sku_code, sku_name, location, qty, picked_qty, shortage_qty, status";

    private OrderItem map(ResultSet rs, int rowNum) throws SQLException {
        OrderItem it = new OrderItem();
        it.setId(rs.getLong("id"));
        it.setOrderId(rs.getLong("order_id"));
        it.setSkuId(rs.getLong("sku_id"));
        it.setSkuCode(rs.getString("sku_code"));
        it.setSkuName(rs.getString("sku_name"));
        it.setLocation(rs.getString("location"));
        it.setQty(rs.getInt("qty"));
        it.setPickedQty(rs.getInt("picked_qty"));
        it.setShortageQty(rs.getInt("shortage_qty"));
        it.setStatus(rs.getString("status"));
        return it;
    }

    public List<OrderItem> findByOrderId(Long orderId) {
        return jdbc.query("SELECT " + COLS + " FROM order_items WHERE order_id = ? ORDER BY id", this::map, orderId);
    }

    public List<OrderItem> findByOrderIds(List<Long> orderIds) {
        if (orderIds == null || orderIds.isEmpty()) {
            return new ArrayList<>();
        }
        String in = String.join(",", orderIds.stream().map(String::valueOf).toList());
        return jdbc.query("SELECT " + COLS + " FROM order_items WHERE order_id IN (" + in + ") ORDER BY order_id, id", this::map);
    }

    public int insert(OrderItem it) {
        return jdbc.update(
                "INSERT INTO order_items(order_id, sku_id, sku_code, sku_name, location, qty, picked_qty, shortage_qty, status) "
                        + "VALUES(?,?,?,?,?,?,0,0,'待拣')",
                it.getOrderId(), it.getSkuId(), it.getSkuCode(), it.getSkuName(), it.getLocation(),
                it.getQty() == null ? 0 : it.getQty());
    }

    public int deleteByOrderId(Long orderId) {
        return jdbc.update("DELETE FROM order_items WHERE order_id = ?", orderId);
    }

    /** 按 SKU 聚合所选订单的明细，用于生成波次拣货清单（按库位排序）。 */
    public List<WaveItem> aggregateBySku(List<Long> orderIds) {
        if (orderIds == null || orderIds.isEmpty()) {
            return new ArrayList<>();
        }
        String in = String.join(",", orderIds.stream().map(String::valueOf).toList());
        String sql = "SELECT sku_id, MAX(sku_code) AS sku_code, MAX(sku_name) AS sku_name, MAX(location) AS location, "
                + "SUM(qty) AS required_qty FROM order_items WHERE order_id IN (" + in + ") "
                + "GROUP BY sku_id ORDER BY location";
        return jdbc.query(sql, (rs, rowNum) -> {
            WaveItem wi = new WaveItem();
            wi.setSkuId(rs.getLong("sku_id"));
            wi.setSkuCode(rs.getString("sku_code"));
            wi.setSkuName(rs.getString("sku_name"));
            wi.setLocation(rs.getString("location"));
            wi.setRequiredQty(rs.getInt("required_qty"));
            wi.setPickedQty(0);
            wi.setShortageQty(0);
            wi.setStatus("待拣");
            return wi;
        });
    }

    public int updatePicked(Long orderId, String skuCode, int pickedQty, int shortageQty, String status) {
        return jdbc.update(
                "UPDATE order_items SET picked_qty = ?, shortage_qty = ?, status = ? WHERE order_id = ? AND sku_code = ?",
                pickedQty, shortageQty, status, orderId, skuCode);
    }

    public int updateStatusByOrderIds(List<Long> orderIds, String status) {
        if (orderIds == null || orderIds.isEmpty()) {
            return 0;
        }
        String in = String.join(",", orderIds.stream().map(String::valueOf).toList());
        return jdbc.update("UPDATE order_items SET status = ? WHERE order_id IN (" + in + ")", status);
    }

    /** 拣货完成后回写订单明细：full=true 视为足量拣齐，否则记为缺货 */
    public int markPickBySku(List<Long> orderIds, String skuCode, boolean full) {
        if (orderIds == null || orderIds.isEmpty()) {
            return 0;
        }
        String in = String.join(",", orderIds.stream().map(String::valueOf).toList());
        int flag = full ? 1 : 0;
        return jdbc.update(
                "UPDATE order_items SET status = ?, picked_qty = CASE WHEN ?=1 THEN qty ELSE 0 END, "
                        + "shortage_qty = CASE WHEN ?=1 THEN 0 ELSE qty END "
                        + "WHERE order_id IN (" + in + ") AND sku_code = ?",
                full ? "已拣" : "缺货", flag, flag, skuCode);
    }

    public long sumQtyByOrderIds(List<Long> orderIds) {
        if (orderIds == null || orderIds.isEmpty()) {
            return 0;
        }
        String in = String.join(",", orderIds.stream().map(String::valueOf).toList());
        Long n = jdbc.queryForObject("SELECT COALESCE(SUM(qty),0) FROM order_items WHERE order_id IN (" + in + ")", Long.class);
        return n == null ? 0 : n;
    }
}
