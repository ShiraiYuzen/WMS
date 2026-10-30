package com.example.wms.dao;

import com.example.wms.entity.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

@Repository
public class OrderDao {

    private final JdbcTemplate jdbc;

    public OrderDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final String COLS = "id, order_no, platform, customer_name, customer_phone, address, item_count, total_qty, "
            + "status, wave_id, wave_no, priority, create_time, wave_time, finish_time";

    private Order map(ResultSet rs, int rowNum) throws SQLException {
        Order o = new Order();
        o.setId(rs.getLong("id"));
        o.setOrderNo(rs.getString("order_no"));
        o.setPlatform(rs.getString("platform"));
        o.setCustomerName(rs.getString("customer_name"));
        o.setCustomerPhone(rs.getString("customer_phone"));
        o.setAddress(rs.getString("address"));
        o.setItemCount(rs.getInt("item_count"));
        o.setTotalQty(rs.getInt("total_qty"));
        o.setStatus(rs.getString("status"));
        long waveId = rs.getLong("wave_id");
        o.setWaveId(rs.wasNull() ? null : waveId);
        o.setWaveNo(rs.getString("wave_no"));
        o.setPriority(rs.getString("priority"));
        o.setCreateTime(rs.getString("create_time"));
        o.setWaveTime(rs.getString("wave_time"));
        o.setFinishTime(rs.getString("finish_time"));
        return o;
    }

    private String where(String status, String platform, String keyword, List<Object> args) {
        StringBuilder sb = new StringBuilder(" WHERE 1=1");
        if (status != null && !status.isBlank()) {
            sb.append(" AND status = ?");
            args.add(status);
        }
        if (platform != null && !platform.isBlank()) {
            sb.append(" AND platform = ?");
            args.add(platform);
        }
        if (keyword != null && !keyword.isBlank()) {
            sb.append(" AND (order_no LIKE ? OR customer_name LIKE ?)");
            String like = "%" + keyword.trim() + "%";
            args.add(like);
            args.add(like);
        }
        return sb.toString();
    }

    public long count(String status, String platform, String keyword) {
        List<Object> args = new ArrayList<>();
        String sql = "SELECT COUNT(*) FROM orders" + where(status, platform, keyword, args);
        Long n = jdbc.queryForObject(sql, Long.class, args.toArray());
        return n == null ? 0 : n;
    }

    public List<Order> findPage(String status, String platform, String keyword, int offset, int size) {
        List<Object> args = new ArrayList<>();
        String sql = "SELECT " + COLS + " FROM orders" + where(status, platform, keyword, args)
                + " ORDER BY id DESC LIMIT ? OFFSET ?";
        args.add(size);
        args.add(offset);
        return jdbc.query(sql, this::map, args.toArray());
    }

    public List<Order> findPending(int limit) {
        return jdbc.query("SELECT " + COLS + " FROM orders WHERE status = '待分波' ORDER BY priority DESC, id ASC LIMIT ?",
                this::map, limit);
    }

    public Order findById(Long id) {
        List<Order> list = jdbc.query("SELECT " + COLS + " FROM orders WHERE id = ?", this::map, id);
        return list.isEmpty() ? null : list.get(0);
    }

    public List<Order> findByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return new ArrayList<>();
        }
        String in = String.join(",", ids.stream().map(String::valueOf).toList());
        return jdbc.query("SELECT " + COLS + " FROM orders WHERE id IN (" + in + ") ORDER BY id", this::map);
    }

    public List<Order> findByWaveId(Long waveId) {
        return jdbc.query("SELECT " + COLS + " FROM orders WHERE wave_id = ? ORDER BY id", this::map, waveId);
    }

    public long insert(Order o) {
        KeyHolder kh = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO orders(order_no, platform, customer_name, customer_phone, address, item_count, total_qty, status, priority, create_time) "
                            + "VALUES(?,?,?,?,?,?,?,?,?,NOW())",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, o.getOrderNo());
            ps.setString(2, o.getPlatform());
            ps.setString(3, o.getCustomerName());
            ps.setString(4, o.getCustomerPhone());
            ps.setString(5, o.getAddress());
            ps.setInt(6, o.getItemCount() == null ? 0 : o.getItemCount());
            ps.setInt(7, o.getTotalQty() == null ? 0 : o.getTotalQty());
            ps.setString(8, "待分波");
            ps.setString(9, o.getPriority() == null ? "普通" : o.getPriority());
            return ps;
        }, kh);
        Number key = kh.getKey();
        return key == null ? 0 : key.longValue();
    }

    public int updateStatus(Long id, String status) {
        return jdbc.update("UPDATE orders SET status=? WHERE id=?", status, id);
    }

    public int updateStatusByIds(List<Long> ids, String status) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        String in = String.join(",", ids.stream().map(String::valueOf).toList());
        return jdbc.update("UPDATE orders SET status=? WHERE id IN (" + in + ")", status);
    }

    public int assignWave(List<Long> ids, Long waveId, String waveNo) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        String in = String.join(",", ids.stream().map(String::valueOf).toList());
        return jdbc.update("UPDATE orders SET status='已分波', wave_id=?, wave_no=?, wave_time=NOW() WHERE id IN (" + in + ")",
                waveId, waveNo);
    }

    /** 取消波次时把订单退回「待分波」并清空波次归属 */
    public int clearWave(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        String in = String.join(",", ids.stream().map(String::valueOf).toList());
        return jdbc.update("UPDATE orders SET status='待分波', wave_id=NULL, wave_no=NULL, wave_time=NULL WHERE id IN (" + in + ")");
    }

    public int finishByIds(List<Long> ids, String status) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        String in = String.join(",", ids.stream().map(String::valueOf).toList());
        return jdbc.update("UPDATE orders SET status=?, finish_time=NOW() WHERE id IN (" + in + ")", status);
    }

    public long countByStatus(String status) {
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM orders WHERE status = ?", Long.class, status);
        return n == null ? 0 : n;
    }

    public long countAll() {
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM orders", Long.class);
        return n == null ? 0 : n;
    }

    public long countToday() {
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM orders WHERE DATE(create_time) = CURDATE()", Long.class);
        return n == null ? 0 : n;
    }

    /** 各平台订单量 */
    public List<java.util.Map<String, Object>> countGroupByPlatform() {
        return jdbc.queryForList("SELECT platform AS name, COUNT(*) AS value FROM orders GROUP BY platform ORDER BY value DESC");
    }

    /** 近 7 天订单量 */
    public List<java.util.Map<String, Object>> countRecentDays() {
        return jdbc.queryForList(
                "SELECT DATE_FORMAT(create_time, '%m-%d') AS name, COUNT(*) AS value FROM orders "
                        + "WHERE create_time >= DATE_SUB(CURDATE(), INTERVAL 6 DAY) "
                        + "GROUP BY DATE_FORMAT(create_time, '%m-%d') ORDER BY MIN(create_time)");
    }
}
