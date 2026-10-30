package com.example.wms.dao;

import com.example.wms.entity.Shortage;
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
public class ShortageDao {

    private final JdbcTemplate jdbc;

    public ShortageDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final String COLS = "id, wave_id, wave_no, sku_id, sku_code, sku_name, location, required_qty, "
            + "picked_qty, short_qty, reason, reporter_id, reporter_name, status, handle_remark, create_time, handle_time";

    private Shortage map(ResultSet rs, int rowNum) throws SQLException {
        Shortage s = new Shortage();
        s.setId(rs.getLong("id"));
        long wid = rs.getLong("wave_id");
        s.setWaveId(rs.wasNull() ? null : wid);
        s.setWaveNo(rs.getString("wave_no"));
        s.setSkuId(rs.getLong("sku_id"));
        s.setSkuCode(rs.getString("sku_code"));
        s.setSkuName(rs.getString("sku_name"));
        s.setLocation(rs.getString("location"));
        s.setRequiredQty(rs.getInt("required_qty"));
        s.setPickedQty(rs.getInt("picked_qty"));
        s.setShortQty(rs.getInt("short_qty"));
        s.setReason(rs.getString("reason"));
        long rid = rs.getLong("reporter_id");
        s.setReporterId(rs.wasNull() ? null : rid);
        s.setReporterName(rs.getString("reporter_name"));
        s.setStatus(rs.getString("status"));
        s.setHandleRemark(rs.getString("handle_remark"));
        s.setCreateTime(rs.getString("create_time"));
        s.setHandleTime(rs.getString("handle_time"));
        return s;
    }

    private String where(String status, String keyword, List<Object> args) {
        StringBuilder sb = new StringBuilder(" WHERE 1=1");
        if (status != null && !status.isBlank()) {
            sb.append(" AND status = ?");
            args.add(status);
        }
        if (keyword != null && !keyword.isBlank()) {
            sb.append(" AND (wave_no LIKE ? OR sku_code LIKE ? OR sku_name LIKE ?)");
            String like = "%" + keyword.trim() + "%";
            args.add(like); args.add(like); args.add(like);
        }
        return sb.toString();
    }

    public long count(String status, String keyword) {
        List<Object> args = new ArrayList<>();
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM shortages" + where(status, keyword, args), Long.class, args.toArray());
        return n == null ? 0 : n;
    }

    public List<Shortage> findPage(String status, String keyword, int offset, int size) {
        List<Object> args = new ArrayList<>();
        String sql = "SELECT " + COLS + " FROM shortages" + where(status, keyword, args) + " ORDER BY id DESC LIMIT ? OFFSET ?";
        args.add(size);
        args.add(offset);
        return jdbc.query(sql, this::map, args.toArray());
    }

    public List<Shortage> findByWaveId(Long waveId) {
        return jdbc.query("SELECT " + COLS + " FROM shortages WHERE wave_id = ? ORDER BY id", this::map, waveId);
    }

    public long insert(Shortage s) {
        KeyHolder kh = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO shortages(wave_id, wave_no, sku_id, sku_code, sku_name, location, required_qty, picked_qty, "
                            + "short_qty, reason, reporter_id, reporter_name, status, create_time) VALUES(?,?,?,?,?,?,?,?,?,?,?,?, '待处理', NOW())",
                    Statement.RETURN_GENERATED_KEYS);
            if (s.getWaveId() == null) {
                ps.setNull(1, java.sql.Types.BIGINT);
            } else {
                ps.setLong(1, s.getWaveId());
            }
            ps.setString(2, s.getWaveNo());
            ps.setLong(3, s.getSkuId());
            ps.setString(4, s.getSkuCode());
            ps.setString(5, s.getSkuName());
            ps.setString(6, s.getLocation());
            ps.setInt(7, s.getRequiredQty() == null ? 0 : s.getRequiredQty());
            ps.setInt(8, s.getPickedQty() == null ? 0 : s.getPickedQty());
            ps.setInt(9, s.getShortQty() == null ? 0 : s.getShortQty());
            ps.setString(10, s.getReason());
            if (s.getReporterId() == null) {
                ps.setNull(11, java.sql.Types.BIGINT);
                ps.setNull(12, java.sql.Types.VARCHAR);
            } else {
                ps.setLong(11, s.getReporterId());
                ps.setString(12, s.getReporterName());
            }
            return ps;
        }, kh);
        Number key = kh.getKey();
        return key == null ? 0 : key.longValue();
    }

    public int handle(Long id, String status, String remark) {
        return jdbc.update("UPDATE shortages SET status=?, handle_remark=?, handle_time=NOW() WHERE id=?", status, remark, id);
    }

    public long countByStatus(String status) {
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM shortages WHERE status = ?", Long.class, status);
        return n == null ? 0 : n;
    }

    public long countAll() {
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM shortages", Long.class);
        return n == null ? 0 : n;
    }
}
