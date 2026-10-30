package com.example.wms.common;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSetMetaData;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 通用 JDBC 行映射工具：
 * 数据库列名（snake_case）自动转成 camelCase 作为 JSON key，
 * 省去为 15 张表各写一套 Entity/RowMapper——前端 A 期望的正是 camelCase 字段名。
 */
public class JdbcMaps {

    public static final RowMapper<Map<String, Object>> CAMEL = (rs, rowNum) -> {
        Map<String, Object> m = new HashMap<>();
        ResultSetMetaData md = rs.getMetaData();
        int n = md.getColumnCount();
        for (int i = 1; i <= n; i++) {
            String key = snakeToCamel(md.getColumnLabel(i));
            m.put(key, rs.getObject(i));
        }
        return m;
    };

    public static String snakeToCamel(String s) {
        if (s == null) return null;
        StringBuilder sb = new StringBuilder();
        boolean up = false;
        for (char c : s.toCharArray()) {
            if (c == '_') { up = true; continue; }
            sb.append(up ? Character.toUpperCase(c) : c);
            up = false;
        }
        return sb.toString();
    }

    public static List<Map<String, Object>> list(JdbcTemplate jdbc, String sql, Object... args) {
        return jdbc.query(sql, CAMEL, args);
    }

    public static Map<String, Object> one(JdbcTemplate jdbc, String sql, Object... args) {
        List<Map<String, Object>> l = jdbc.query(sql, CAMEL, args);
        return l.isEmpty() ? null : l.get(0);
    }

    public static long count(JdbcTemplate jdbc, String sql, Object... args) {
        Long v = jdbc.queryForObject(sql, Long.class, args);
        return v == null ? 0 : v;
    }

    /**
     * 分页查询：返回 A 前端约定的 { records, total, pageNum, pageSize, pages }。
     * 包装原 SQL 做 COUNT，再 LIMIT/OFFSET 取页（参数顺序与原 SQL 一致）。
     */
    public static Map<String, Object> page(JdbcTemplate jdbc, String sql, int pageNum, int pageSize, Object... args) {
        long total = count(jdbc, "SELECT COUNT(*) FROM (" + sql + ") _t", args);
        int from = (pageNum - 1) * pageSize;
        String psql = sql + " LIMIT " + pageSize + " OFFSET " + from;
        List<Map<String, Object>> rows = jdbc.query(psql, CAMEL, args);
        Map<String, Object> r = new HashMap<>();
        r.put("records", rows);
        r.put("total", total);
        r.put("pageNum", pageNum);
        r.put("pageSize", pageSize);
        r.put("pages", total == 0 ? 1 : (long) Math.ceil((double) total / pageSize));
        return r;
    }
}
