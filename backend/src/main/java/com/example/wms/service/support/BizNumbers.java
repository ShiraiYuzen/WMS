package com.example.wms.service.support;

import com.example.wms.common.SqlSupport;
import org.springframework.stereotype.Component;

import java.text.SimpleDateFormat;
import java.util.Date;

@Component
public class BizNumbers {

    private final SqlSupport sql;

    public BizNumbers(SqlSupport sql) {
        this.sql = sql;
    }

    public static String now() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
    }

    public static String today() {
        return new SimpleDateFormat("yyyy-MM-dd").format(new Date());
    }

    public static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    public static int i(Object o) {
        if (o == null) return 0;
        if (o instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(String.valueOf(o));
        } catch (Exception e) {
            return 0;
        }
    }

    public static double d(Object o) {
        if (o == null) return 0d;
        if (o instanceof Number n) return n.doubleValue();
        try {
            return Double.parseDouble(String.valueOf(o));
        } catch (Exception e) {
            return 0d;
        }
    }

    private String nextNo(String prefix, String table, String column) {
        String p = prefix + today().replace("-", "");
        long c = sql.count("SELECT COUNT(*) FROM " + table + " WHERE " + column + " LIKE ?", p + "%");
        return p + String.format("%03d", c + 1);
    }

    public String nextWaveNo() {
        return nextNo("WV", "wave", "wave_no");
    }

    public String nextReviewNo() {
        return nextNo("RV", "review", "review_no");
    }

    public String nextShortageNo() {
        return nextNo("SH", "shortage", "shortage_no");
    }

    public String nextHandoverNo() {
        return nextNo("HO", "handover", "handover_no");
    }
}
