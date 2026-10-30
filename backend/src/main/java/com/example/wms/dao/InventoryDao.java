package com.example.wms.dao;

import com.example.wms.entity.Inventory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

@Repository
public class InventoryDao {

    private final JdbcTemplate jdbc;

    public InventoryDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final String COLS = "id, sku_id, sku_code, sku_name, location, total_qty, locked_qty, available_qty, update_time";

    private Inventory map(ResultSet rs, int rowNum) throws SQLException {
        Inventory i = new Inventory();
        i.setId(rs.getLong("id"));
        i.setSkuId(rs.getLong("sku_id"));
        i.setSkuCode(rs.getString("sku_code"));
        i.setSkuName(rs.getString("sku_name"));
        i.setLocation(rs.getString("location"));
        i.setTotalQty(rs.getInt("total_qty"));
        i.setLockedQty(rs.getInt("locked_qty"));
        i.setAvailableQty(rs.getInt("available_qty"));
        i.setUpdateTime(rs.getString("update_time"));
        return i;
    }

    public long count(String keyword) {
        String sql = "SELECT COUNT(*) FROM inventory";
        if (keyword != null && !keyword.isBlank()) {
            String like = "%" + keyword.trim() + "%";
            Long n = jdbc.queryForObject(sql + " WHERE sku_code LIKE ? OR sku_name LIKE ? OR location LIKE ?",
                    Long.class, like, like, like);
            return n == null ? 0 : n;
        }
        Long n = jdbc.queryForObject(sql, Long.class);
        return n == null ? 0 : n;
    }

    public List<Inventory> findPage(String keyword, int offset, int size) {
        String base = "SELECT " + COLS + " FROM inventory";
        if (keyword != null && !keyword.isBlank()) {
            String like = "%" + keyword.trim() + "%";
            return jdbc.query(base + " WHERE sku_code LIKE ? OR sku_name LIKE ? OR location LIKE ? ORDER BY location LIMIT ? OFFSET ?",
                    this::map, like, like, like, size, offset);
        }
        return jdbc.query(base + " ORDER BY location LIMIT ? OFFSET ?", this::map, size, offset);
    }

    public Inventory findBySkuId(Long skuId) {
        List<Inventory> list = jdbc.query("SELECT " + COLS + " FROM inventory WHERE sku_id = ? LIMIT 1", this::map, skuId);
        return list.isEmpty() ? null : list.get(0);
    }

    public List<Inventory> findBySkuIds(List<Long> skuIds) {
        if (skuIds == null || skuIds.isEmpty()) {
            return new ArrayList<>();
        }
        String in = String.join(",", skuIds.stream().map(String::valueOf).toList());
        return jdbc.query("SELECT " + COLS + " FROM inventory WHERE sku_id IN (" + in + ")", this::map);
    }

    /** 波次生成时锁定库存：locked += qty, available -= qty */
    public int lock(Long skuId, int qty) {
        return jdbc.update("UPDATE inventory SET locked_qty = locked_qty + ?, available_qty = available_qty - ?, update_time = NOW() WHERE sku_id = ?",
                qty, qty, skuId);
    }

    /** 取消波次时释放锁定：locked -= qty, available += qty */
    public int unlock(Long skuId, int qty) {
        return jdbc.update("UPDATE inventory SET locked_qty = GREATEST(locked_qty - ?, 0), available_qty = available_qty + ?, update_time = NOW() WHERE sku_id = ?",
                qty, qty, skuId);
    }

    /** 出库时扣减实际库存：total -= qty, locked -= qty */
    public int deduct(Long skuId, int qty) {
        return jdbc.update("UPDATE inventory SET total_qty = GREATEST(total_qty - ?, 0), locked_qty = GREATEST(locked_qty - ?, 0), update_time = NOW() WHERE sku_id = ?",
                qty, qty, skuId);
    }

    /** 复核发现少货/破损补回库存：total += qty */
    public int increase(Long skuId, int qty) {
        return jdbc.update("UPDATE inventory SET total_qty = total_qty + ?, available_qty = available_qty + ?, update_time = NOW() WHERE sku_id = ?",
                qty, qty, skuId);
    }

    public int updateBase(Inventory inv) {
        return jdbc.update("UPDATE inventory SET total_qty=?, available_qty=?, update_time=NOW() WHERE id=?",
                inv.getTotalQty(), inv.getTotalQty() - (inv.getLockedQty() == null ? 0 : inv.getLockedQty()), inv.getId());
    }

    public long countLowStock(int threshold) {
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM inventory WHERE available_qty <= ?", Long.class, threshold);
        return n == null ? 0 : n;
    }
}
