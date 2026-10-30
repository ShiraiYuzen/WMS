package com.example.wms.dao;

import com.example.wms.entity.Carrier;
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
public class CarrierDao {

    private final JdbcTemplate jdbc;

    public CarrierDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final String COLS = "id, carrier_code, carrier_name, contact, phone, status, remark, create_time";

    private Carrier map(ResultSet rs, int rowNum) throws SQLException {
        Carrier c = new Carrier();
        c.setId(rs.getLong("id"));
        c.setCarrierCode(rs.getString("carrier_code"));
        c.setCarrierName(rs.getString("carrier_name"));
        c.setContact(rs.getString("contact"));
        c.setPhone(rs.getString("phone"));
        c.setStatus(rs.getString("status"));
        c.setRemark(rs.getString("remark"));
        c.setCreateTime(rs.getString("create_time"));
        return c;
    }

    private String where(String keyword, List<Object> args) {
        StringBuilder sb = new StringBuilder(" WHERE 1=1");
        if (keyword != null && !keyword.isBlank()) {
            sb.append(" AND (carrier_code LIKE ? OR carrier_name LIKE ? OR contact LIKE ?)");
            String like = "%" + keyword.trim() + "%";
            args.add(like); args.add(like); args.add(like);
        }
        return sb.toString();
    }

    public long count(String keyword) {
        List<Object> args = new ArrayList<>();
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM carrier" + where(keyword, args), Long.class, args.toArray());
        return n == null ? 0 : n;
    }

    public List<Carrier> findPage(String keyword, int offset, int size) {
        List<Object> args = new ArrayList<>();
        String sql = "SELECT " + COLS + " FROM carrier" + where(keyword, args) + " ORDER BY id LIMIT ? OFFSET ?";
        args.add(size); args.add(offset);
        return jdbc.query(sql, this::map, args.toArray());
    }

    public List<Carrier> findAllEnabled() {
        return jdbc.query("SELECT " + COLS + " FROM carrier WHERE status='启用' ORDER BY id", this::map);
    }

    public Carrier findById(Long id) {
        List<Carrier> list = jdbc.query("SELECT " + COLS + " FROM carrier WHERE id = ?", this::map, id);
        return list.isEmpty() ? null : list.get(0);
    }

    public long insert(Carrier c) {
        KeyHolder kh = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO carrier(carrier_code, carrier_name, contact, phone, status, remark, create_time) "
                            + "VALUES(?,?,?,?,?,?,NOW())", Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, c.getCarrierCode());
            ps.setString(2, c.getCarrierName());
            ps.setString(3, c.getContact());
            ps.setString(4, c.getPhone());
            ps.setString(5, c.getStatus());
            ps.setString(6, c.getRemark());
            return ps;
        }, kh);
        Number key = kh.getKey();
        return key == null ? 0 : key.longValue();
    }

    public int update(Carrier c) {
        return jdbc.update("UPDATE carrier SET carrier_code=?, carrier_name=?, contact=?, phone=?, status=?, remark=? WHERE id=?",
                c.getCarrierCode(), c.getCarrierName(), c.getContact(), c.getPhone(), c.getStatus(), c.getRemark(), c.getId());
    }

    public int delete(Long id) {
        return jdbc.update("DELETE FROM carrier WHERE id=?", id);
    }
}
