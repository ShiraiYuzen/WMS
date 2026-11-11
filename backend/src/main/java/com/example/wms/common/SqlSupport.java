package com.example.wms.common;

import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 动态 / 多表 SQL 辅助：走 Spring 管理的 DataSource 连接（参与事务），
 * 结果列名转为 camelCase。单表 CRUD 优先用各业务 Mapper + MyBatis-Plus。
 */
@Component
public class SqlSupport {

    private final DataSource dataSource;

    public SqlSupport(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public List<Map<String, Object>> list(String sql, Object... args) {
        Connection c = DataSourceUtils.getConnection(dataSource);
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            bind(ps, args);
            try (ResultSet rs = ps.executeQuery()) {
                List<Map<String, Object>> rows = new ArrayList<>();
                while (rs.next()) {
                    rows.add(Maps.rowToCamelMap(rs));
                }
                return rows;
            }
        } catch (SQLException e) {
            throw new RuntimeException("SQL list failed: " + sql, e);
        } finally {
            DataSourceUtils.releaseConnection(c, dataSource);
        }
    }

    public Map<String, Object> one(String sql, Object... args) {
        List<Map<String, Object>> l = list(sql, args);
        return l.isEmpty() ? null : l.get(0);
    }

    public long count(String sql, Object... args) {
        Connection c = DataSourceUtils.getConnection(dataSource);
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            bind(ps, args);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("SQL count failed: " + sql, e);
        } finally {
            DataSourceUtils.releaseConnection(c, dataSource);
        }
        return 0;
    }

    public int update(String sql, Object... args) {
        Connection c = DataSourceUtils.getConnection(dataSource);
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            bind(ps, args);
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("SQL update failed: " + sql, e);
        } finally {
            DataSourceUtils.releaseConnection(c, dataSource);
        }
    }

    /** 分页：返回 { records, total, pageNum, pageSize, pages }。 */
    public Map<String, Object> page(String sql, int pageNum, int pageSize, Object... args) {
        long total = count("SELECT COUNT(*) FROM (" + sql + ") _t", args);
        int from = (pageNum - 1) * pageSize;
        String psql = sql + " LIMIT " + pageSize + " OFFSET " + from;
        List<Map<String, Object>> rows = list(psql, args);
        Map<String, Object> r = new HashMap<>();
        r.put("records", rows);
        r.put("total", total);
        r.put("pageNum", pageNum);
        r.put("pageSize", pageSize);
        r.put("pages", total == 0 ? 1 : (long) Math.ceil((double) total / pageSize));
        return r;
    }

    private static void bind(PreparedStatement ps, Object... args) throws SQLException {
        if (args == null) return;
        for (int i = 0; i < args.length; i++) {
            ps.setObject(i + 1, args[i]);
        }
    }
}
