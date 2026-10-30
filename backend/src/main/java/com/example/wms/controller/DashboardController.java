package com.example.wms.controller;

import com.example.wms.common.AuthContext;
import com.example.wms.common.JdbcMaps;
import com.example.wms.common.LoginUser;
import com.example.wms.dto.ApiResult;
import com.example.wms.service.Repo;
import com.example.wms.service.Stats;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final Repo repo;
    private final Stats stats;

    public DashboardController(Repo repo, Stats stats) {
        this.repo = repo;
        this.stats = stats;
    }

    /**
     * 看板汇总。
     *
     * <p>「我的待办」按角色裁剪：只返回当前角色能处理的那几项，且数量按本人归属统计
     * （拣货员只数派给自己的任务，复核员只数自己名下的复核单），
     * 避免任何账号都能看到全仓的待办数量。</p>
     */
    @GetMapping("/summary")
    public ApiResult<Map<String, Object>> summary(@RequestParam(defaultValue = "7") int days) {
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
            // 「待拣货」= 我名下还没结单的任务，含正在拣的
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
        return ApiResult.ok(r);
    }

    @GetMapping("/trend")
    public ApiResult<List<Map<String, Object>>> trend(@RequestParam(defaultValue = "7") int days) {
        return ApiResult.ok(stats.trend(startOf(days), LocalDate.now().toString()));
    }

    /** 把前端传的天数（今日=1 / 近7天 / 近30天）换算成起始日期，并夹在 1~90 天内防越界 */
    private static String startOf(int days) {
        int span = Math.min(Math.max(days, 1), 90);
        return LocalDate.now().minusDays(span - 1L).toString();
    }

    private long count(String sql) {
        return JdbcMaps.count(repo.jdbc(), sql);
    }

    private long count(String sql, Object... args) {
        return JdbcMaps.count(repo.jdbc(), sql, args);
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
