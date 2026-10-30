package com.example.wms.service;

import com.example.wms.common.Dict;
import com.example.wms.common.JdbcMaps;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * 通用仓储与 VO 组装：所有 Controller 共用的查询/编号/留痕/描述补全逻辑。
 */
@Component
public class Repo {

    private final JdbcTemplate jdbc;

    public Repo(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public JdbcTemplate jdbc() {
        return jdbc;
    }

    // ---------------- 时间/编号 ----------------

    public static String now() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
    }

    public static String today() {
        return new SimpleDateFormat("yyyy-MM-dd").format(new Date());
    }

    private String nextNo(String prefix, String table, String column) {
        String p = prefix + today().replace("-", "");
        Long c = jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE " + column + " LIKE ?", Long.class, p + "%");
        return p + String.format("%03d", (c == null ? 0 : c) + 1);
    }

    public String nextWaveNo()     { return nextNo("WV", "wave", "wave_no"); }
    public String nextReviewNo()   { return nextNo("RV", "review", "review_no"); }
    public String nextShortageNo() { return nextNo("SH", "shortage", "shortage_no"); }
    public String nextHandoverNo() { return nextNo("HO", "handover", "handover_no"); }

    // ---------------- 字段工具 ----------------

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

    // ---------------- 订单 ----------------

    public Map<String, Object> orderVo(String orderNo) {
        Map<String, Object> o = JdbcMaps.one(jdbc, "SELECT * FROM `order` WHERE order_no=?", orderNo);
        if (o == null) return null;
        enrichOrder(o);
        o.put("items", JdbcMaps.list(jdbc,
                "SELECT * FROM order_item WHERE order_no=? ORDER BY line_no", orderNo));
        return o;
    }

    public void enrichOrder(Map<String, Object> o) {
        String st = str(o.get("status"));
        o.put("statusDesc", Dict.desc("orderStatus", st));
        o.put("priorityDesc", Dict.desc("priority", str(o.get("priority"))));
        o.put("poolable", "CREATED".equals(st) || "POOLED".equals(st));
    }

    // ---------------- 波次 ----------------

    public Map<String, Object> waveVo(String waveNo) {
        Map<String, Object> w = JdbcMaps.one(jdbc, "SELECT * FROM wave WHERE wave_no=?", waveNo);
        if (w == null) return null;
        enrichWave(w);
        List<Map<String, Object>> orders = JdbcMaps.list(jdbc,
                "SELECT o.* FROM `order` o JOIN wave_order wo ON wo.order_no=o.order_no " +
                        "WHERE wo.wave_no=? ORDER BY wo.seq_no", waveNo);
        for (Map<String, Object> o : orders) enrichOrder(o);
        w.put("orders", orders);
        return w;
    }

    public void enrichWave(Map<String, Object> w) {
        String st = str(w.get("status"));
        w.put("statusDesc", Dict.desc("waveStatus", st));
        w.put("strategyDesc", Dict.desc("waveStrategy", str(w.get("strategy"))));
        w.put("progress", waveProgress(st));
        w.put("currentNode", waveNode(st));
    }

    private int waveProgress(String st) {
        if (st == null) return 0;
        return switch (st) {
            case "CREATED" -> 5;
            case "ASSIGNED" -> 20;
            case "PICKING" -> 45;
            case "PICKED" -> 65;
            case "REVIEWING" -> 78;
            case "REVIEWED" -> 90;
            case "HANDOVERED" -> 100;
            case "CLOSED" -> 100;
            default -> 0;
        };
    }

    private String waveNode(String st) {
        if (st == null) return "待分配";
        return switch (st) {
            case "CREATED" -> "待分配拣货员";
            case "ASSIGNED" -> "待开始拣货";
            case "PICKING" -> "拣货执行中";
            case "PICKED" -> "拣货完成，待复核";
            case "REVIEWING" -> "复核打包中";
            case "REVIEWED" -> "复核完成，待交接";
            case "HANDOVERED" -> "已出库交接";
            case "CLOSED" -> "已归档";
            case "CANCELLED" -> "已取消";
            default -> st;
        };
    }

    // ---------------- 拣货任务 ----------------

    public Map<String, Object> pickTaskVo(String taskNo) {
        Map<String, Object> t = JdbcMaps.one(jdbc, "SELECT * FROM pick_task WHERE task_no=?", taskNo);
        if (t == null) return null;
        enrichTask(t);
        t.put("items", JdbcMaps.list(jdbc,
                "SELECT * FROM pick_item WHERE task_no=? ORDER BY seq_no", taskNo));
        return t;
    }

    public void enrichTask(Map<String, Object> t) {
        t.put("statusDesc", Dict.desc("pickTaskStatus", str(t.get("status"))));
        int itemCount = i(t.get("itemCount"));
        int finished = i(t.get("finishedCount"));
        t.put("progress", itemCount == 0 ? 0 : Math.min(100, Math.round(finished * 100f / itemCount)));
    }

    // ---------------- 复核 ----------------

    public Map<String, Object> reviewVo(String reviewNo) {
        Map<String, Object> r = JdbcMaps.one(jdbc, "SELECT * FROM review WHERE review_no=?", reviewNo);
        if (r == null) return null;
        enrichReview(r);
        r.put("shouldItems", JdbcMaps.list(jdbc,
                "SELECT * FROM order_item WHERE order_no=? ORDER BY line_no", str(r.get("orderNo"))));
        r.put("diffItems", JdbcMaps.list(jdbc,
                "SELECT * FROM review_diff WHERE review_no=? ORDER BY id", reviewNo));
        return r;
    }

    public void enrichReview(Map<String, Object> r) {
        r.put("statusDesc", Dict.desc("reviewStatus", str(r.get("status"))));
        r.put("diffTypeDesc", Dict.desc("diffType", str(r.get("diffType"))));
    }

    // ---------------- 缺货 ----------------

    public Map<String, Object> shortageVo(long id) {
        Map<String, Object> s = JdbcMaps.one(jdbc, "SELECT * FROM shortage WHERE id=?", id);
        if (s == null) return null;
        enrichShortage(s);
        return s;
    }

    public void enrichShortage(Map<String, Object> s) {
        s.put("statusDesc", Dict.desc("shortageStatus", str(s.get("status"))));
        s.put("reasonDesc", Dict.desc("shortageReason", str(s.get("reason"))));
        s.put("handleTypeDesc", Dict.desc("shortageHandleType", str(s.get("handleType"))));
    }

    // ---------------- 交接 ----------------

    public Map<String, Object> handoverVo(String handoverNo) {
        Map<String, Object> h = JdbcMaps.one(jdbc, "SELECT * FROM handover WHERE handover_no=?", handoverNo);
        if (h == null) return null;
        h.put("statusDesc", Dict.desc("handoverStatus", str(h.get("status"))));
        return h;
    }

    // ---------------- 留痕 ----------------

    public void log(String bizType, String bizNo, String action, String actionDesc,
                    String operatorNo, String operatorName, String operatorRole, String detail) {
        String t = now();
        jdbc.update("INSERT INTO operation_log (biz_type,biz_no,action,action_desc,operator_no,operator_name," +
                        "operator_role,detail,operate_time,create_time) VALUES (?,?,?,?,?,?,?,?,?,?)",
                bizType, bizNo, action, actionDesc, operatorNo, operatorName, operatorRole, detail, t, t);
    }

    // ---------------- 杂项 ----------------

    public String employeeName(String employeeNo) {
        if (employeeNo == null) return null;
        Map<String, Object> e = JdbcMaps.one(jdbc, "SELECT employee_name FROM employee WHERE employee_no=?", employeeNo);
        return e == null ? null : str(e.get("employeeName"));
    }

    public String carrierName(String carrierCode) {
        if (carrierCode == null) return null;
        Map<String, Object> c = JdbcMaps.one(jdbc, "SELECT carrier_name FROM carrier WHERE carrier_code=?", carrierCode);
        return c == null ? null : str(c.get("carrierName"));
    }
}
