package com.example.wms.dao;

import com.example.wms.entity.Outbound;
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
public class OutboundDao {

    private final JdbcTemplate jdbc;

    public OutboundDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final String COLS = "id, wave_id, wave_no, order_count, total_qty, carrier, handover_to, "
            + "operator_id, operator_name, status, remark, create_time, handover_time";

    private Outbound map(ResultSet rs, int rowNum) throws SQLException {
        Outbound o = new Outbound();
        o.setId(rs.getLong("id"));
        long wid = rs.getLong("wave_id");
        o.setWaveId(rs.wasNull() ? null : wid);
        o.setWaveNo(rs.getString("wave_no"));
        o.setOrderCount(rs.getInt("order_count"));
        o.setTotalQty(rs.getInt("total_qty"));
        o.setCarrier(rs.getString("carrier"));
        o.setHandoverTo(rs.getString("handover_to"));
        long op = rs.getLong("operator_id");
        o.setOperatorId(rs.wasNull() ? null : op);
        o.setOperatorName(rs.getString("operator_name"));
        o.setStatus(rs.getString("status"));
        o.setRemark(rs.getString("remark"));
        o.setCreateTime(rs.getString("create_time"));
        o.setHandoverTime(rs.getString("handover_time"));
        return o;
    }

    private String where(String keyword, List<Object> args) {
        StringBuilder sb = new StringBuilder(" WHERE 1=1");
        if (keyword != null && !keyword.isBlank()) {
            sb.append(" AND (wave_no LIKE ? OR carrier LIKE ? OR handover_to LIKE ?)");
            String like = "%" + keyword.trim() + "%";
            args.add(like); args.add(like); args.add(like);
        }
        return sb.toString();
    }

    public long count(String keyword) {
        List<Object> args = new ArrayList<>();
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM outbounds" + where(keyword, args), Long.class, args.toArray());
        return n == null ? 0 : n;
    }

    public List<Outbound> findPage(String keyword, int offset, int size) {
        List<Object> args = new ArrayList<>();
        String sql = "SELECT " + COLS + " FROM outbounds" + where(keyword, args) + " ORDER BY id DESC LIMIT ? OFFSET ?";
        args.add(size);
        args.add(offset);
        return jdbc.query(sql, this::map, args.toArray());
    }

    public List<Outbound> findByWaveId(Long waveId) {
        return jdbc.query("SELECT " + COLS + " FROM outbounds WHERE wave_id = ? ORDER BY id", this::map, waveId);
    }

    public long insert(Outbound o) {
        KeyHolder kh = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO outbounds(wave_id, wave_no, order_count, total_qty, carrier, handover_to, "
                            + "operator_id, operator_name, status, remark, create_time, handover_time) "
                            + "VALUES(?,?,?,?,?,?,?,?, '已交接', ?, NOW(), NOW())",
                    Statement.RETURN_GENERATED_KEYS);
            if (o.getWaveId() == null) ps.setNull(1, java.sql.Types.BIGINT); else ps.setLong(1, o.getWaveId());
            ps.setString(2, o.getWaveNo());
            ps.setInt(3, o.getOrderCount() == null ? 0 : o.getOrderCount());
            ps.setInt(4, o.getTotalQty() == null ? 0 : o.getTotalQty());
            ps.setString(5, o.getCarrier());
            ps.setString(6, o.getHandoverTo());
            if (o.getOperatorId() == null) { ps.setNull(7, java.sql.Types.BIGINT); ps.setNull(8, java.sql.Types.VARCHAR); }
            else { ps.setLong(7, o.getOperatorId()); ps.setString(8, o.getOperatorName()); }
            ps.setString(9, o.getRemark());
            return ps;
        }, kh);
        Number key = kh.getKey();
        return key == null ? 0 : key.longValue();
    }

    public long countAll() {
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM outbounds", Long.class);
        return n == null ? 0 : n;
    }

    public long sumOutboundQty() {
        Long n = jdbc.queryForObject("SELECT COALESCE(SUM(total_qty),0) FROM outbounds", Long.class);
        return n == null ? 0 : n;
    }

    public long sumOutboundOrders() {
        Long n = jdbc.queryForObject("SELECT COALESCE(SUM(order_count),0) FROM outbounds", Long.class);
        return n == null ? 0 : n;
    }
}
