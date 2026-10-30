package com.example.wms.dao;

import com.example.wms.entity.Sku;
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
public class SkuDao {

    private final JdbcTemplate jdbc;

    public SkuDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final String COLS = "id, sku_code, name, spec, category, location, create_time";

    private Sku map(ResultSet rs, int rowNum) throws SQLException {
        Sku s = new Sku();
        s.setId(rs.getLong("id"));
        s.setSkuCode(rs.getString("sku_code"));
        s.setName(rs.getString("name"));
        s.setSpec(rs.getString("spec"));
        s.setCategory(rs.getString("category"));
        s.setLocation(rs.getString("location"));
        s.setCreateTime(rs.getString("create_time"));
        return s;
    }

    public long count(String keyword, String category) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM skus WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (keyword != null && !keyword.isBlank()) {
            sql.append(" AND (sku_code LIKE ? OR name LIKE ? OR location LIKE ?)");
            String like = "%" + keyword.trim() + "%";
            args.add(like); args.add(like); args.add(like);
        }
        if (category != null && !category.isBlank()) {
            sql.append(" AND category = ?");
            args.add(category);
        }
        Long n = jdbc.queryForObject(sql.toString(), Long.class, args.toArray());
        return n == null ? 0 : n;
    }

    public List<Sku> findPage(String keyword, String category, int offset, int size) {
        StringBuilder sql = new StringBuilder("SELECT " + COLS + " FROM skus WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (keyword != null && !keyword.isBlank()) {
            sql.append(" AND (sku_code LIKE ? OR name LIKE ? OR location LIKE ?)");
            String like = "%" + keyword.trim() + "%";
            args.add(like); args.add(like); args.add(like);
        }
        if (category != null && !category.isBlank()) {
            sql.append(" AND category = ?");
            args.add(category);
        }
        sql.append(" ORDER BY sku_code LIMIT ? OFFSET ?");
        args.add(size); args.add(offset);
        return jdbc.query(sql.toString(), this::map, args.toArray());
    }

    public List<Sku> findAll() {
        return jdbc.query("SELECT " + COLS + " FROM skus ORDER BY sku_code", this::map);
    }

    public Sku findById(Long id) {
        List<Sku> list = jdbc.query("SELECT " + COLS + " FROM skus WHERE id = ?", this::map, id);
        return list.isEmpty() ? null : list.get(0);
    }

    public Sku findByCode(String skuCode) {
        List<Sku> list = jdbc.query("SELECT " + COLS + " FROM skus WHERE sku_code = ?", this::map, skuCode);
        return list.isEmpty() ? null : list.get(0);
    }

    public long insert(Sku s) {
        KeyHolder kh = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO skus(sku_code, name, spec, category, location, create_time) VALUES(?,?,?,?,?,NOW())",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, s.getSkuCode());
            ps.setString(2, s.getName());
            ps.setString(3, s.getSpec());
            ps.setString(4, s.getCategory());
            ps.setString(5, s.getLocation());
            return ps;
        }, kh);
        Number key = kh.getKey();
        return key == null ? 0 : key.longValue();
    }

    public int update(Sku s) {
        return jdbc.update("UPDATE skus SET name=?, spec=?, category=?, location=? WHERE id=?",
                s.getName(), s.getSpec(), s.getCategory(), s.getLocation(), s.getId());
    }

    public int delete(Long id) {
        return jdbc.update("DELETE FROM skus WHERE id=?", id);
    }
}
