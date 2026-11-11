package com.example.wms.service;

import com.example.wms.common.AuthContext;
import com.example.wms.common.LoginUser;
import com.example.wms.common.SqlSupport;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class DashboardService {

    private final SqlSupport sql;
    private final Stats stats;

    public DashboardService(SqlSupport sql, Stats stats) {
        this.sql = sql;
        this.stats = stats;
    }

    public Map<String, Object> summary(int days) {
        LoginUser me = AuthContext.require();
        boolean admin = me.hasRole("ADMIN");

        String end = LocalDate.now().toString();
        String start = startOf(days);
        Map<String, Object> overview = stats.overview(start, end);

        List<Map<String, Object>> pipeline = new ArrayList<>();
        pipeline.add(stage("订单汇聚",
                count("SELECT COUNT(*) FROM `order` WHERE status='CREATED'"),
                count("SELECT COUNT(*) FROM `order` WHERE status='POOLED'")));
        pipeline.add(stage("波次生成",
                count("SELECT COUNT(*) FROM wave WHERE status='CREATED'"),
                count("SELECT COUNT(*) FROM wave WHERE status='ASSIGNED'")));
        pipeline.add(stage("拣货执行",
                count("SELECT COUNT(*) FROM pick_task WHERE status='WAITING'"),
                count("SELECT COUNT(*) FROM pick_task WHERE status='PICKING'")));
        pipeline.add(stage("复核打包",
                count("SELECT COUNT(*) FROM review WHERE status='WAITING'"),
                count("SELECT COUNT(*) FROM review WHERE status='DIFF'")));
        pipeline.add(stage("出库交接",
                count("SELECT COUNT(*) FROM wave WHERE status='REVIEWED'"),
                count("SELECT COUNT(*) FROM wave WHERE status='HANDOVERED'")));

        List<Map<String, Object>> todos = new ArrayList<>();
        if (admin || me.hasRole("WAVE_OPERATOR")) {
            todos.add(todo("待汇聚订单", count("SELECT COUNT(*) FROM `order` WHERE status='CREATED'"), "/order", "WAVE_OPERATOR"));
            todos.add(todo("待生成波次订单", count("SELECT COUNT(*) FROM `order` WHERE status='POOLED'"), "/wave", "WAVE_OPERATOR"));
            todos.add(todo("待处理缺货", count("SELECT COUNT(*) FROM shortage WHERE status='PENDING'"), "/shortage", "WAVE_OPERATOR"));
        }
        if (admin || me.hasRole("PICKER")) {
            long n = admin
                    ? count("SELECT COUNT(*) FROM pick_task WHERE status IN ('WAITING','PICKING')")
                    : count("SELECT COUNT(*) FROM pick_task WHERE status IN ('WAITING','PICKING') AND picker_no=?",
                            me.employeeNo());
            todos.add(todo("待拣货任务", n, "/pick", "PICKER"));
        }
        if (admin || me.hasRole("REVIEWER")) {
            long n = admin
                    ? count("SELECT COUNT(*) FROM review WHERE status='WAITING'")
                    : count("SELECT COUNT(*) FROM review WHERE status='WAITING' AND reviewer_no=?", me.employeeNo());
            todos.add(todo("待复核订单", n, "/review", "REVIEWER"));
            todos.add(todo("待交接波次", count("SELECT COUNT(*) FROM wave WHERE status='REVIEWED'"), "/handover", "REVIEWER"));
        }

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("overview", overview);
        r.put("pipeline", pipeline);
        r.put("todos", todos);
        return r;
    }

    public List<Map<String, Object>> trend(int days) {
        return stats.trend(startOf(days), LocalDate.now().toString());
    }

    private static String startOf(int days) {
        int span = Math.min(Math.max(days, 1), 90);
        return LocalDate.now().minusDays(span - 1L).toString();
    }

    private long count(String s) {
        return sql.count(s);
    }

    private long count(String s, Object... args) {
        return sql.count(s, args);
    }

    private static Map<String, Object> stage(String stage, long pending, long working) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("stage", stage);
        m.put("pending", pending);
        m.put("working", working);
        return m;
    }

    private static Map<String, Object> todo(String name, long count, String path, String role) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", name);
        m.put("count", count);
        m.put("path", path);
        m.put("role", role);
        return m;
    }
}
