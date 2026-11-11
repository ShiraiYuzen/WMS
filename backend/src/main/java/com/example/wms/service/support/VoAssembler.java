package com.example.wms.service.support;

import com.example.wms.common.Dict;
import com.example.wms.common.SqlSupport;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class VoAssembler {

    private final SqlSupport sql;

    public VoAssembler(SqlSupport sql) {
        this.sql = sql;
    }

    public Map<String, Object> orderVo(String orderNo) {
        Map<String, Object> o = sql.one("SELECT * FROM `order` WHERE order_no=?", orderNo);
        if (o == null) return null;
        enrichOrder(o);
        o.put("items", sql.list("SELECT * FROM order_item WHERE order_no=? ORDER BY line_no", orderNo));
        return o;
    }

    public void enrichOrder(Map<String, Object> o) {
        String st = BizNumbers.str(o.get("status"));
        o.put("statusDesc", Dict.desc("orderStatus", st));
        o.put("priorityDesc", Dict.desc("priority", BizNumbers.str(o.get("priority"))));
        o.put("poolable", "CREATED".equals(st) || "POOLED".equals(st));
    }

    public Map<String, Object> waveVo(String waveNo) {
        Map<String, Object> w = sql.one("SELECT * FROM wave WHERE wave_no=?", waveNo);
        if (w == null) return null;
        enrichWave(w);
        List<Map<String, Object>> orders = sql.list(
                "SELECT o.* FROM `order` o JOIN wave_order wo ON wo.order_no=o.order_no " +
                        "WHERE wo.wave_no=? ORDER BY wo.seq_no", waveNo);
        for (Map<String, Object> o : orders) enrichOrder(o);
        w.put("orders", orders);
        return w;
    }

    public void enrichWave(Map<String, Object> w) {
        String st = BizNumbers.str(w.get("status"));
        w.put("statusDesc", Dict.desc("waveStatus", st));
        w.put("strategyDesc", Dict.desc("waveStrategy", BizNumbers.str(w.get("strategy"))));
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

    public Map<String, Object> pickTaskVo(String taskNo) {
        Map<String, Object> t = sql.one("SELECT * FROM pick_task WHERE task_no=?", taskNo);
        if (t == null) return null;
        enrichTask(t);
        t.put("items", sql.list("SELECT * FROM pick_item WHERE task_no=? ORDER BY seq_no", taskNo));
        return t;
    }

    public void enrichTask(Map<String, Object> t) {
        t.put("statusDesc", Dict.desc("pickTaskStatus", BizNumbers.str(t.get("status"))));
        int itemCount = BizNumbers.i(t.get("itemCount"));
        int finished = BizNumbers.i(t.get("finishedCount"));
        t.put("progress", itemCount == 0 ? 0 : Math.min(100, Math.round(finished * 100f / itemCount)));
    }

    public Map<String, Object> reviewVo(String reviewNo) {
        Map<String, Object> r = sql.one("SELECT * FROM review WHERE review_no=?", reviewNo);
        if (r == null) return null;
        enrichReview(r);
        r.put("shouldItems", sql.list(
                "SELECT * FROM order_item WHERE order_no=? ORDER BY line_no", BizNumbers.str(r.get("orderNo"))));
        r.put("diffItems", sql.list("SELECT * FROM review_diff WHERE review_no=? ORDER BY id", reviewNo));
        return r;
    }

    public void enrichReview(Map<String, Object> r) {
        r.put("statusDesc", Dict.desc("reviewStatus", BizNumbers.str(r.get("status"))));
        r.put("diffTypeDesc", Dict.desc("diffType", BizNumbers.str(r.get("diffType"))));
    }

    public Map<String, Object> shortageVo(long id) {
        Map<String, Object> s = sql.one("SELECT * FROM shortage WHERE id=?", id);
        if (s == null) return null;
        enrichShortage(s);
        return s;
    }

    public void enrichShortage(Map<String, Object> s) {
        s.put("statusDesc", Dict.desc("shortageStatus", BizNumbers.str(s.get("status"))));
        s.put("reasonDesc", Dict.desc("shortageReason", BizNumbers.str(s.get("reason"))));
        s.put("handleTypeDesc", Dict.desc("shortageHandleType", BizNumbers.str(s.get("handleType"))));
    }

    public Map<String, Object> handoverVo(String handoverNo) {
        Map<String, Object> h = sql.one("SELECT * FROM handover WHERE handover_no=?", handoverNo);
        if (h == null) return null;
        h.put("statusDesc", Dict.desc("handoverStatus", BizNumbers.str(h.get("status"))));
        return h;
    }

    public String employeeName(String employeeNo) {
        if (employeeNo == null) return null;
        Map<String, Object> e = sql.one("SELECT employee_name FROM employee WHERE employee_no=?", employeeNo);
        return e == null ? null : BizNumbers.str(e.get("employeeName"));
    }

    public String carrierName(String carrierCode) {
        if (carrierCode == null) return null;
        Map<String, Object> c = sql.one("SELECT carrier_name FROM carrier WHERE carrier_code=?", carrierCode);
        return c == null ? null : BizNumbers.str(c.get("carrierName"));
    }
}
