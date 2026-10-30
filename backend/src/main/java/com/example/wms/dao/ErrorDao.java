package com.example.wms.dao;

import com.example.wms.entity.ErrorRecord;
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
import java.util.Map;

@Repository
public class ErrorDao {

    private final JdbcTemplate jdbc;

    public ErrorDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final String COLS = "id, wave_id, wave_no, order_id, order_no, sku_id, sku_code, sku_name, "
            + "error_type, qty, checker_id, checker_name, description, create_time";

    private ErrorRecord map(ResultSet rs, int rowNum) throws SQLException {
        ErrorRecord e = new ErrorRecord();
        e.setId(rs.getLong("id"));
        long wid = rs.getLong("wave_id");
        e.setWaveId(rs.wasNull() ? null : wid);
        e.setWaveNo(rs.getString("wave_no"));
        long oid = rs.getLong("order_id");
        e.setOrderId(rs.wasNull() ? null : oid);
        e.setOrderNo(rs.getString("order_no"));
        long sid = rs.getLong("sku_id");
        e.setSkuId(rs.wasNull() ? null : sid);
        e.setSkuCode(rs.getString("sku_code"));
        e.setSkuName(rs.getString("sku_name"));
        e.setErrorType(rs.getString("error_type"));
        e.setQty(rs.getInt("qty"));
        long cid = rs.getLong("checker_id");
        e.setCheckerId(rs.wasNull() ? null : cid);
        e.setCheckerName(rs.getString("checker_name"));
        e.setDescription(rs.getString("description"));
        e.setCreateTime(rs.getString("create_time"));
        return e;
    }

    private String where(String errorType, String keyword, List<Object> args) {
        StringBuilder sb = new StringBuilder(" WHERE 1=1");
        if (errorType != null && !errorType.isBlank()) {
            sb.append(" AND error_type = ?");
            args.add(errorType);
        }
        if (keyword != null && !keyword.isBlank()) {
            sb.append(" AND (wave_no LIKE ? OR order_no LIKE ? OR sku_code LIKE ? OR checker_name LIKE ?)");
            String like = "%" + keyword.trim() + "%";
            args.add(like); args.add(like); args.add(like); args.add(like);
        }
        return sb.toString();
    }

    public long count(String errorType, String keyword) {
        List<Object> args = new ArrayList<>();
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM error_records" + where(errorType, keyword, args), Long.class, args.toArray());
        return n == null ? 0 : n;
    }

    public List<ErrorRecord> findPage(String errorType, String keyword, int offset, int size) {
        List<Object> args = new ArrayList<>();
        String sql = "SELECT " + COLS + " FROM error_records" + where(errorType, keyword, args) + " ORDER BY id DESC LIMIT ? OFFSET ?";
        args.add(size);
        args.add(offset);
        return jdbc.query(sql, this::map, args.toArray());
    }

    public long insert(ErrorRecord e) {
        KeyHolder kh = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO error_records(wave_id, wave_no, order_id, order_no, sku_id, sku_code, sku_name, "
                            + "error_type, qty, checker_id, checker_name, description, create_time) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,NOW())",
                    Statement.RETURN_GENERATED_KEYS);
            if (e.getWaveId() == null) ps.setNull(1, java.sql.Types.BIGINT); else ps.setLong(1, e.getWaveId());
            ps.setString(2, e.getWaveNo());
            if (e.getOrderId() == null) ps.setNull(3, java.sql.Types.BIGINT); else ps.setLong(3, e.getOrderId());
            ps.setString(4, e.getOrderNo());
            if (e.getSkuId() == null) ps.setNull(5, java.sql.Types.BIGINT); else ps.setLong(5, e.getSkuId());
            ps.setString(6, e.getSkuCode());
            ps.setString(7, e.getSkuName());
            ps.setString(8, e.getErrorType());
            ps.setInt(9, e.getQty() == null ? 1 : e.getQty());
            if (e.getCheckerId() == null) { ps.setNull(10, java.sql.Types.BIGINT); ps.setNull(11, java.sql.Types.VARCHAR); }
            else { ps.setLong(10, e.getCheckerId()); ps.setString(11, e.getCheckerName()); }
            ps.setString(12, e.getDescription());
            return ps;
        }, kh);
        Number key = kh.getKey();
        return key == null ? 0 : key.longValue();
    }

    public long countAll() {
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM error_records", Long.class);
        return n == null ? 0 : n;
    }

    /** 差错类型分布 */
    public List<Map<String, Object>> countGroupByType() {
        return jdbc.queryForList("SELECT error_type AS name, COUNT(*) AS value FROM error_records GROUP BY error_type ORDER BY value DESC");
    }

    /** 按拣货员归因的差错数（通过波次关联） */
    public List<Map<String, Object>> countGroupByPicker() {
        return jdbc.queryForList(
                "SELECT w.picker_name AS name, COUNT(e.id) AS errorCount, COALESCE(SUM(e.qty),0) AS errorQty "
                        + "FROM error_records e JOIN waves w ON e.wave_id = w.id "
                        + "WHERE w.picker_name IS NOT NULL GROUP BY w.picker_name ORDER BY errorCount DESC");
    }

    /** 近 7 天差错趋势 */
    public List<Map<String, Object>> countRecentDays() {
        return jdbc.queryForList(
                "SELECT DATE_FORMAT(create_time, '%m-%d') AS name, COUNT(*) AS value FROM error_records "
                        + "WHERE create_time >= DATE_SUB(CURDATE(), INTERVAL 6 DAY) "
                        + "GROUP BY DATE_FORMAT(create_time, '%m-%d') ORDER BY MIN(create_time)");
    }
}
