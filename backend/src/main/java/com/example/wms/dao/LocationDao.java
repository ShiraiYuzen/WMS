package com.example.wms.dao;

import com.example.wms.entity.WarehouseLocation;
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
public class LocationDao {

    private final JdbcTemplate jdbc;

    public LocationDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final String COLS = "id, location_code, zone, type, status, capacity, remark, create_time";

    private WarehouseLocation map(ResultSet rs, int rowNum) throws SQLException {
        WarehouseLocation l = new WarehouseLocation();
        l.setId(rs.getLong("id"));
        l.setLocationCode(rs.getString("location_code"));
        l.setZone(rs.getString("zone"));
        l.setType(rs.getString("type"));
        l.setStatus(rs.getString("status"));
        l.setCapacity(rs.getInt("capacity"));
        l.setRemark(rs.getString("remark"));
        l.setCreateTime(rs.getString("create_time"));
        return l;
    }

    private String where(String zone, String keyword, List<Object> args) {
        StringBuilder sb = new StringBuilder(" WHERE 1=1");
        if (zone != null && !zone.isBlank()) {
            sb.append(" AND zone = ?");
            args.add(zone);
        }
        if (keyword != null && !keyword.isBlank()) {
            sb.append(" AND (location_code LIKE ? OR remark LIKE ?)");
            String like = "%" + keyword.trim() + "%";
            args.add(like); args.add(like);
        }
        return sb.toString();
    }

    public long count(String zone, String keyword) {
        List<Object> args = new ArrayList<>();
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM warehouse_location" + where(zone, keyword, args), Long.class, args.toArray());
        return n == null ? 0 : n;
    }

    public List<WarehouseLocation> findPage(String zone, String keyword, int offset, int size) {
        List<Object> args = new ArrayList<>();
        String sql = "SELECT " + COLS + " FROM warehouse_location" + where(zone, keyword, args)
                + " ORDER BY location_code LIMIT ? OFFSET ?";
        args.add(size); args.add(offset);
        return jdbc.query(sql, this::map, args.toArray());
    }

    public List<WarehouseLocation> findAll() {
        return jdbc.query("SELECT " + COLS + " FROM warehouse_location ORDER BY location_code", this::map);
    }

    public List<String> findZones() {
        return jdbc.queryForList("SELECT DISTINCT zone FROM warehouse_location WHERE zone IS NOT NULL ORDER BY zone", String.class);
    }

    public WarehouseLocation findById(Long id) {
        List<WarehouseLocation> list = jdbc.query("SELECT " + COLS + " FROM warehouse_location WHERE id = ?", this::map, id);
        return list.isEmpty() ? null : list.get(0);
    }

    public long insert(WarehouseLocation l) {
        KeyHolder kh = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO warehouse_location(location_code, zone, type, status, capacity, remark, create_time) "
                            + "VALUES(?,?,?,?,?,?,NOW())", Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, l.getLocationCode());
            ps.setString(2, l.getZone());
            ps.setString(3, l.getType());
            ps.setString(4, l.getStatus());
            ps.setInt(5, l.getCapacity() == null ? 0 : l.getCapacity());
            ps.setString(6, l.getRemark());
            return ps;
        }, kh);
        Number key = kh.getKey();
        return key == null ? 0 : key.longValue();
    }

    public int update(WarehouseLocation l) {
        return jdbc.update("UPDATE warehouse_location SET location_code=?, zone=?, type=?, status=?, capacity=?, remark=? WHERE id=?",
                l.getLocationCode(), l.getZone(), l.getType(), l.getStatus(),
                l.getCapacity() == null ? 0 : l.getCapacity(), l.getRemark(), l.getId());
    }

    public int delete(Long id) {
        return jdbc.update("DELETE FROM warehouse_location WHERE id=?", id);
    }

    public int countByStatus(String status) {
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM warehouse_location WHERE status=?", Long.class, status);
        return n == null ? 0 : n.intValue();
    }
}
