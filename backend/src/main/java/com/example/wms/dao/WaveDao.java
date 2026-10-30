package com.example.wms.dao;

import com.example.wms.entity.Wave;
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
public class WaveDao {

    private final JdbcTemplate jdbc;

    public WaveDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final String COLS = "id, wave_no, status, order_count, item_count, total_qty, picker_id, picker_name, "
            + "checker_id, checker_name, creator_id, creator_name, strategy, remark, create_time, start_time, "
            + "pick_finish_time, review_finish_time, outbound_time";

    private Wave map(ResultSet rs, int rowNum) throws SQLException {
        Wave w = new Wave();
        w.setId(rs.getLong("id"));
        w.setWaveNo(rs.getString("wave_no"));
        w.setStatus(rs.getString("status"));
        w.setOrderCount(rs.getInt("order_count"));
        w.setItemCount(rs.getInt("item_count"));
        w.setTotalQty(rs.getInt("total_qty"));
        long pid = rs.getLong("picker_id");
        w.setPickerId(rs.wasNull() ? null : pid);
        w.setPickerName(rs.getString("picker_name"));
        long cid = rs.getLong("checker_id");
        w.setCheckerId(rs.wasNull() ? null : cid);
        w.setCheckerName(rs.getString("checker_name"));
        long cr = rs.getLong("creator_id");
        w.setCreatorId(rs.wasNull() ? null : cr);
        w.setCreatorName(rs.getString("creator_name"));
        w.setStrategy(rs.getString("strategy"));
        w.setRemark(rs.getString("remark"));
        w.setCreateTime(rs.getString("create_time"));
        w.setStartTime(rs.getString("start_time"));
        w.setPickFinishTime(rs.getString("pick_finish_time"));
        w.setReviewFinishTime(rs.getString("review_finish_time"));
        w.setOutboundTime(rs.getString("outbound_time"));
        return w;
    }

    private String where(String status, String keyword, List<Object> args) {
        StringBuilder sb = new StringBuilder(" WHERE 1=1");
        if (status != null && !status.isBlank()) {
            sb.append(" AND status = ?");
            args.add(status);
        }
        if (keyword != null && !keyword.isBlank()) {
            sb.append(" AND (wave_no LIKE ? OR picker_name LIKE ? OR checker_name LIKE ?)");
            String like = "%" + keyword.trim() + "%";
            args.add(like); args.add(like); args.add(like);
        }
        return sb.toString();
    }

    public long count(String status, String keyword) {
        List<Object> args = new ArrayList<>();
        String sql = "SELECT COUNT(*) FROM waves" + where(status, keyword, args);
        Long n = jdbc.queryForObject(sql, Long.class, args.toArray());
        return n == null ? 0 : n;
    }

    public List<Wave> findPage(String status, String keyword, int offset, int size) {
        List<Object> args = new ArrayList<>();
        String sql = "SELECT " + COLS + " FROM waves" + where(status, keyword, args) + " ORDER BY id DESC LIMIT ? OFFSET ?";
        args.add(size);
        args.add(offset);
        return jdbc.query(sql, this::map, args.toArray());
    }

    public List<Wave> findByStatus(String status) {
        return jdbc.query("SELECT " + COLS + " FROM waves WHERE status = ? ORDER BY id DESC", this::map, status);
    }

    public Wave findById(Long id) {
        List<Wave> list = jdbc.query("SELECT " + COLS + " FROM waves WHERE id = ?", this::map, id);
        return list.isEmpty() ? null : list.get(0);
    }

    public long insert(Wave w) {
        KeyHolder kh = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO waves(wave_no, status, order_count, item_count, total_qty, picker_id, picker_name, "
                            + "creator_id, creator_name, strategy, remark, create_time) VALUES(?,?,?,?,?,?,?,?,?,?,?,NOW())",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, w.getWaveNo());
            ps.setString(2, w.getStatus());
            ps.setInt(3, w.getOrderCount() == null ? 0 : w.getOrderCount());
            ps.setInt(4, w.getItemCount() == null ? 0 : w.getItemCount());
            ps.setInt(5, w.getTotalQty() == null ? 0 : w.getTotalQty());
            if (w.getPickerId() == null) {
                ps.setNull(6, java.sql.Types.BIGINT);
                ps.setNull(7, java.sql.Types.VARCHAR);
            } else {
                ps.setLong(6, w.getPickerId());
                ps.setString(7, w.getPickerName());
            }
            if (w.getCreatorId() == null) {
                ps.setNull(8, java.sql.Types.BIGINT);
                ps.setNull(9, java.sql.Types.VARCHAR);
            } else {
                ps.setLong(8, w.getCreatorId());
                ps.setString(9, w.getCreatorName());
            }
            ps.setString(10, w.getStrategy());
            ps.setString(11, w.getRemark());
            return ps;
        }, kh);
        Number key = kh.getKey();
        return key == null ? 0 : key.longValue();
    }

    public int updateStatus(Long id, String status) {
        return jdbc.update("UPDATE waves SET status=? WHERE id=?", status, id);
    }

    public int assignPicker(Long id, Long pickerId, String pickerName) {
        return jdbc.update("UPDATE waves SET picker_id=?, picker_name=? WHERE id=?", pickerId, pickerName, id);
    }

    public int assignChecker(Long id, Long checkerId, String checkerName) {
        return jdbc.update("UPDATE waves SET checker_id=?, checker_name=? WHERE id=?", checkerId, checkerName, id);
    }

    public int markStart(Long id) {
        return jdbc.update("UPDATE waves SET status='拣货中', start_time=NOW() WHERE id=?", id);
    }

    public int markPickFinish(Long id) {
        return jdbc.update("UPDATE waves SET status='待复核', pick_finish_time=NOW() WHERE id=?", id);
    }

    public int markReviewFinish(Long id) {
        return jdbc.update("UPDATE waves SET status='待出库', review_finish_time=NOW() WHERE id=?", id);
    }

    public int markOutbound(Long id) {
        return jdbc.update("UPDATE waves SET status='已出库', outbound_time=NOW() WHERE id=?", id);
    }

    public long countByStatus(String status) {
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM waves WHERE status = ?", Long.class, status);
        return n == null ? 0 : n;
    }

    public long countAll() {
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM waves", Long.class);
        return n == null ? 0 : n;
    }

    public long countTodayWaves() {
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM waves WHERE DATE(create_time) = CURDATE()", Long.class);
        return n == null ? 0 : n;
    }
}
