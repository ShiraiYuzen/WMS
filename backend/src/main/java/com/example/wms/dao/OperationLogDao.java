package com.example.wms.dao;

import com.example.wms.entity.OperationLog;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

@Repository
public class OperationLogDao {

    private final JdbcTemplate jdbc;

    public OperationLogDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final String COLS = "id, module, action, method, uri, operator, role, detail, cost_ms, result, create_time";

    private OperationLog map(ResultSet rs, int rowNum) throws SQLException {
        OperationLog l = new OperationLog();
        l.setId(rs.getLong("id"));
        l.setModule(rs.getString("module"));
        l.setAction(rs.getString("action"));
        l.setMethod(rs.getString("method"));
        l.setUri(rs.getString("uri"));
        l.setOperator(rs.getString("operator"));
        l.setRole(rs.getString("role"));
        l.setDetail(rs.getString("detail"));
        l.setCostMs(rs.getLong("cost_ms"));
        l.setResult(rs.getString("result"));
        l.setCreateTime(rs.getString("create_time"));
        return l;
    }

    private String where(String module, String keyword, List<Object> args) {
        StringBuilder sb = new StringBuilder(" WHERE 1=1");
        if (module != null && !module.isBlank()) {
            sb.append(" AND module = ?");
            args.add(module);
        }
        if (keyword != null && !keyword.isBlank()) {
            sb.append(" AND (operator LIKE ? OR uri LIKE ? OR detail LIKE ?)");
            String like = "%" + keyword.trim() + "%";
            args.add(like); args.add(like); args.add(like);
        }
        return sb.toString();
    }

    public long count(String module, String keyword) {
        List<Object> args = new ArrayList<>();
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM operation_log" + where(module, keyword, args), Long.class, args.toArray());
        return n == null ? 0 : n;
    }

    public List<OperationLog> findPage(String module, String keyword, int offset, int size) {
        List<Object> args = new ArrayList<>();
        String sql = "SELECT " + COLS + " FROM operation_log" + where(module, keyword, args) + " ORDER BY id DESC LIMIT ? OFFSET ?";
        args.add(size); args.add(offset);
        return jdbc.query(sql, this::map, args.toArray());
    }

    public void insert(OperationLog l) {
        jdbc.update("INSERT INTO operation_log(module, action, method, uri, operator, role, detail, cost_ms, result, create_time) "
                        + "VALUES(?,?,?,?,?,?,?,?,?,NOW())",
                l.getModule(), l.getAction(), l.getMethod(), l.getUri(), l.getOperator(), l.getRole(),
                l.getDetail(), l.getCostMs() == null ? 0 : l.getCostMs(), l.getResult());
    }

    public long countAll() {
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM operation_log", Long.class);
        return n == null ? 0 : n;
    }
}
