package com.example.wms.dao;

import com.example.wms.entity.ReviewRecord;
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
public class ReviewDao {

    private final JdbcTemplate jdbc;

    public ReviewDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final String COLS = "id, wave_id, wave_no, order_id, order_no, checker_id, checker_name, result, "
            + "pack_count, error_count, remark, create_time";

    private ReviewRecord map(ResultSet rs, int rowNum) throws SQLException {
        ReviewRecord r = new ReviewRecord();
        r.setId(rs.getLong("id"));
        long wid = rs.getLong("wave_id");
        r.setWaveId(rs.wasNull() ? null : wid);
        r.setWaveNo(rs.getString("wave_no"));
        long oid = rs.getLong("order_id");
        r.setOrderId(rs.wasNull() ? null : oid);
        r.setOrderNo(rs.getString("order_no"));
        long cid = rs.getLong("checker_id");
        r.setCheckerId(rs.wasNull() ? null : cid);
        r.setCheckerName(rs.getString("checker_name"));
        r.setResult(rs.getString("result"));
        r.setPackCount(rs.getInt("pack_count"));
        r.setErrorCount(rs.getInt("error_count"));
        r.setRemark(rs.getString("remark"));
        r.setCreateTime(rs.getString("create_time"));
        return r;
    }

    private String where(String result, String keyword, List<Object> args) {
        StringBuilder sb = new StringBuilder(" WHERE 1=1");
        if (result != null && !result.isBlank()) {
            sb.append(" AND result = ?");
            args.add(result);
        }
        if (keyword != null && !keyword.isBlank()) {
            sb.append(" AND (wave_no LIKE ? OR order_no LIKE ? OR checker_name LIKE ?)");
            String like = "%" + keyword.trim() + "%";
            args.add(like); args.add(like); args.add(like);
        }
        return sb.toString();
    }

    public long count(String result, String keyword) {
        List<Object> args = new ArrayList<>();
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM review_records" + where(result, keyword, args), Long.class, args.toArray());
        return n == null ? 0 : n;
    }

    public List<ReviewRecord> findPage(String result, String keyword, int offset, int size) {
        List<Object> args = new ArrayList<>();
        String sql = "SELECT " + COLS + " FROM review_records" + where(result, keyword, args) + " ORDER BY id DESC LIMIT ? OFFSET ?";
        args.add(size);
        args.add(offset);
        return jdbc.query(sql, this::map, args.toArray());
    }

    public List<ReviewRecord> findByWaveId(Long waveId) {
        return jdbc.query("SELECT " + COLS + " FROM review_records WHERE wave_id = ? ORDER BY id", this::map, waveId);
    }

    public long insert(ReviewRecord r) {
        KeyHolder kh = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO review_records(wave_id, wave_no, order_id, order_no, checker_id, checker_name, result, "
                            + "pack_count, error_count, remark, create_time) VALUES(?,?,?,?,?,?,?,?,?,?,NOW())",
                    Statement.RETURN_GENERATED_KEYS);
            if (r.getWaveId() == null) ps.setNull(1, java.sql.Types.BIGINT); else ps.setLong(1, r.getWaveId());
            ps.setString(2, r.getWaveNo());
            if (r.getOrderId() == null) ps.setNull(3, java.sql.Types.BIGINT); else ps.setLong(3, r.getOrderId());
            ps.setString(4, r.getOrderNo());
            if (r.getCheckerId() == null) { ps.setNull(5, java.sql.Types.BIGINT); ps.setNull(6, java.sql.Types.VARCHAR); }
            else { ps.setLong(5, r.getCheckerId()); ps.setString(6, r.getCheckerName()); }
            ps.setString(7, r.getResult());
            ps.setInt(8, r.getPackCount() == null ? 0 : r.getPackCount());
            ps.setInt(9, r.getErrorCount() == null ? 0 : r.getErrorCount());
            ps.setString(10, r.getRemark());
            return ps;
        }, kh);
        Number key = kh.getKey();
        return key == null ? 0 : key.longValue();
    }

    public long countAll() {
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM review_records", Long.class);
        return n == null ? 0 : n;
    }

    public long countByResult(String result) {
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM review_records WHERE result = ?", Long.class, result);
        return n == null ? 0 : n;
    }

    public long countDistinctOrderReviewed() {
        Long n = jdbc.queryForObject("SELECT COUNT(DISTINCT order_id) FROM review_records", Long.class);
        return n == null ? 0 : n;
    }
}
