package com.example.wms.common;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

/**
 * 把查询结果的 snake_case 列名转成 camelCase。
 */
public final class Maps {

    private Maps() {}

    public static String snakeToCamel(String s) {
        if (s == null) return null;
        StringBuilder sb = new StringBuilder();
        boolean up = false;
        for (char c : s.toCharArray()) {
            if (c == '_') {
                up = true;
                continue;
            }
            sb.append(up ? Character.toUpperCase(c) : c);
            up = false;
        }
        return sb.toString();
    }

    public static Map<String, Object> rowToCamelMap(ResultSet rs) throws SQLException {
        Map<String, Object> m = new HashMap<>();
        ResultSetMetaData md = rs.getMetaData();
        int n = md.getColumnCount();
        for (int i = 1; i <= n; i++) {
            String key = snakeToCamel(md.getColumnLabel(i));
            m.put(key, rs.getObject(i));
        }
        return m;
    }
}
