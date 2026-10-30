package com.example.wms.dao;

import com.example.wms.entity.User;
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
public class UserDao {

    private final JdbcTemplate jdbc;

    public UserDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private User map(ResultSet rs, int rowNum) throws SQLException {
        User u = new User();
        u.setId(rs.getLong("id"));
        u.setUsername(rs.getString("username"));
        u.setPassword(rs.getString("password"));
        u.setNickname(rs.getString("nickname"));
        u.setRole(rs.getString("role"));
        u.setStatus(rs.getString("status"));
        u.setCreateTime(rs.getString("create_time"));
        return u;
    }

    private static final String COLS = "id, username, password, nickname, role, status, create_time";

    public User findByUsername(String username) {
        List<User> list = jdbc.query("SELECT " + COLS + " FROM users WHERE username = ?", this::map, username);
        return list.isEmpty() ? null : list.get(0);
    }

    public User findById(Long id) {
        List<User> list = jdbc.query("SELECT " + COLS + " FROM users WHERE id = ?", this::map, id);
        return list.isEmpty() ? null : list.get(0);
    }

    public List<User> findByRole(String role) {
        return jdbc.query("SELECT " + COLS + " FROM users WHERE role = ? AND status = '启用' ORDER BY id", this::map, role);
    }

    public long count(String keyword, String role) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM users WHERE 1=1");
        java.util.List<Object> args = new java.util.ArrayList<>();
        if (keyword != null && !keyword.isBlank()) {
            sql.append(" AND (username LIKE ? OR nickname LIKE ?)");
            String like = "%" + keyword.trim() + "%";
            args.add(like);
            args.add(like);
        }
        if (role != null && !role.isBlank()) {
            sql.append(" AND role = ?");
            args.add(role);
        }
        Long n = jdbc.queryForObject(sql.toString(), Long.class, args.toArray());
        return n == null ? 0 : n;
    }

    public List<User> findPage(String keyword, String role, int offset, int size) {
        StringBuilder sql = new StringBuilder("SELECT " + COLS + " FROM users WHERE 1=1");
        java.util.List<Object> args = new java.util.ArrayList<>();
        if (keyword != null && !keyword.isBlank()) {
            sql.append(" AND (username LIKE ? OR nickname LIKE ?)");
            String like = "%" + keyword.trim() + "%";
            args.add(like);
            args.add(like);
        }
        if (role != null && !role.isBlank()) {
            sql.append(" AND role = ?");
            args.add(role);
        }
        sql.append(" ORDER BY id DESC LIMIT ? OFFSET ?");
        args.add(size);
        args.add(offset);
        return jdbc.query(sql.toString(), this::map, args.toArray());
    }

    public long insert(User u) {
        KeyHolder kh = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO users(username, password, nickname, role, status, create_time) VALUES(?,?,?,?,?,NOW())",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, u.getUsername());
            ps.setString(2, u.getPassword());
            ps.setString(3, u.getNickname());
            ps.setString(4, u.getRole() == null ? "PICKER" : u.getRole());
            ps.setString(5, u.getStatus() == null ? "启用" : u.getStatus());
            return ps;
        }, kh);
        Number key = kh.getKey();
        return key == null ? 0 : key.longValue();
    }

    public int update(User u) {
        return jdbc.update("UPDATE users SET nickname=?, role=?, status=? WHERE id=?",
                u.getNickname(), u.getRole(), u.getStatus(), u.getId());
    }

    public int updatePassword(Long id, String password) {
        return jdbc.update("UPDATE users SET password=? WHERE id=?", password, id);
    }

    public int delete(Long id) {
        return jdbc.update("DELETE FROM users WHERE id=?", id);
    }
}
