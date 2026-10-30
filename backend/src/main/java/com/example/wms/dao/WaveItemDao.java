package com.example.wms.dao;

import com.example.wms.entity.WaveItem;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

@Repository
public class WaveItemDao {

    private final JdbcTemplate jdbc;

    public WaveItemDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final String COLS = "id, wave_id, sku_id, sku_code, sku_name, location, required_qty, picked_qty, shortage_qty, status";

    private WaveItem map(ResultSet rs, int rowNum) throws SQLException {
        WaveItem wi = new WaveItem();
        wi.setId(rs.getLong("id"));
        wi.setWaveId(rs.getLong("wave_id"));
        wi.setSkuId(rs.getLong("sku_id"));
        wi.setSkuCode(rs.getString("sku_code"));
        wi.setSkuName(rs.getString("sku_name"));
        wi.setLocation(rs.getString("location"));
        wi.setRequiredQty(rs.getInt("required_qty"));
        wi.setPickedQty(rs.getInt("picked_qty"));
        wi.setShortageQty(rs.getInt("shortage_qty"));
        wi.setStatus(rs.getString("status"));
        return wi;
    }

    public List<WaveItem> findByWaveId(Long waveId) {
        return jdbc.query("SELECT " + COLS + " FROM wave_items WHERE wave_id = ? ORDER BY location", this::map, waveId);
    }

    public long insert(WaveItem wi) {
        KeyHolder kh = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO wave_items(wave_id, sku_id, sku_code, sku_name, location, required_qty, picked_qty, shortage_qty, status) "
                            + "VALUES(?,?,?,?,?,?,0,0,'待拣')",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, wi.getWaveId());
            ps.setLong(2, wi.getSkuId());
            ps.setString(3, wi.getSkuCode());
            ps.setString(4, wi.getSkuName());
            ps.setString(5, wi.getLocation());
            ps.setInt(6, wi.getRequiredQty() == null ? 0 : wi.getRequiredQty());
            return ps;
        }, kh);
        Number key = kh.getKey();
        return key == null ? 0 : key.longValue();
    }

    public int updatePicked(Long waveId, String skuCode, int pickedQty, int shortageQty, String status) {
        return jdbc.update(
                "UPDATE wave_items SET picked_qty=?, shortage_qty=?, status=? WHERE wave_id=? AND sku_code=?",
                pickedQty, shortageQty, status, waveId, skuCode);
    }

    public int deleteByWaveId(Long waveId) {
        return jdbc.update("DELETE FROM wave_items WHERE wave_id = ?", waveId);
    }

    public long sumRequiredQty(Long waveId) {
        Long n = jdbc.queryForObject("SELECT COALESCE(SUM(required_qty),0) FROM wave_items WHERE wave_id=?", Long.class, waveId);
        return n == null ? 0 : n;
    }

    public long countUnfinished(Long waveId) {
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM wave_items WHERE wave_id=? AND status='待拣'", Long.class, waveId);
        return n == null ? 0 : n;
    }
}
