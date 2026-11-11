package com.example.wms.service;

import com.example.wms.common.AuthContext;
import com.example.wms.common.Dict;
import com.example.wms.common.ForbiddenException;
import com.example.wms.common.SqlSupport;
import com.example.wms.common.LoginUser;
import com.example.wms.dto.PageResult;
import com.example.wms.service.support.BizNumbers;
import com.example.wms.service.support.OperationLogService;
import com.example.wms.service.support.VoAssembler;
import org.springframework.stereotype.Service;

import java.text.SimpleDateFormat;
import java.util.*;

@Service
public class PickService {

    private final SqlSupport sql;
    private final BizNumbers bizNumbers;
    private final VoAssembler voAssembler;
    private final OperationLogService operationLogService;

    public PickService(SqlSupport sql, BizNumbers bizNumbers, VoAssembler voAssembler, OperationLogService operationLogService) {
        this.sql = sql;
        this.bizNumbers = bizNumbers;
        this.voAssembler = voAssembler;
        this.operationLogService = operationLogService;
    }
    /**
     * 我的拣货任务。
     *
     * <p>拣货员只能看自己的（忽略传入的 pickerNo，防越权查看，SEC-20）；
     * 主管/波次员可按工号筛选，不传则看全部。</p>
     */
    public List<Map<String, Object>> myTasks(String pickerNo) {
        LoginUser me = AuthContext.require();
        String scope = me.hasRole("ADMIN", "WAVE_OPERATOR")
                ? (notBlank(pickerNo) ? pickerNo.trim() : null)
                : me.employeeNo();

        List<Map<String, Object>> rows;
        if (scope != null) {
            rows = sql.list(
                    "SELECT * FROM pick_task WHERE picker_no=? ORDER BY create_time DESC, id DESC", scope);
        } else {
            rows = sql.list(
                    "SELECT * FROM pick_task ORDER BY create_time DESC, id DESC");
        }
        for (Map<String, Object> t : rows) {
            voAssembler.enrichTask(t);
            t.put("items", sql.list(
                    "SELECT * FROM pick_item WHERE task_no=? ORDER BY seq_no", BizNumbers.str(t.get("taskNo"))));
        }
        return rows;
    }

    public Map<String, Object> confirmItem(Map<String, Object> body) {
        long itemId = longVal(body.get("itemId"));
        Map<String, Object> item = sql.one( "SELECT * FROM pick_item WHERE id=?", itemId);
        if (item == null) throw new IllegalArgumentException("拣货明细不存在");

        String taskNo = BizNumbers.str(item.get("taskNo"));
        Map<String, Object> task = requireOperableTask(taskNo);

        int should = BizNumbers.i(item.get("shouldQty"));
        int actual = body.get("actualQty") == null ? should : BizNumbers.i(body.get("actualQty"));
        if (actual < 0) throw new IllegalArgumentException("实拣数量不能为负数");
        if (actual > should) {
            throw new IllegalArgumentException("实拣数量 " + actual + " 超过应拣数量 " + should + "，多货请走复核差异登记");
        }
        String status = actual >= should ? "PICKED" : (actual > 0 ? "PARTIAL" : "PENDING");
        sql.update("UPDATE pick_item SET actual_qty=?, status=?, scan_time=? WHERE id=?",
                actual, status, BizNumbers.now(), itemId);
        recalcTask(taskNo);
        return voAssembler.pickTaskVo(BizNumbers.str(task.get("taskNo")));
    }

    public Map<String, Object> reportShortage(Map<String, Object> body) {
        LoginUser me = AuthContext.require();
        long itemId = longVal(body.get("itemId"));
        Map<String, Object> item = sql.one( "SELECT * FROM pick_item WHERE id=?", itemId);
        if (item == null) throw new IllegalArgumentException("拣货明细不存在");

        String taskNo = BizNumbers.str(item.get("taskNo"));
        Map<String, Object> task = requireOperableTask(taskNo);

        int should = BizNumbers.i(item.get("shouldQty"));
        int actual = BizNumbers.i(item.get("actualQty"));
        int remain = Math.max(1, should - actual);
        int shortage = body.get("shortageQty") == null ? remain : BizNumbers.i(body.get("shortageQty"));
        if (shortage <= 0) shortage = remain;
        // 缺货量必须落在 1 ~ 未拣数量之间，防止上报超过应拣量（SEC-19）
        if (shortage > should - actual) {
            throw new IllegalArgumentException("缺货数量 " + shortage + " 超过未拣数量 " + (should - actual));
        }
        int newActual = Math.max(0, should - shortage);
        String status = newActual > 0 ? "PARTIAL" : "SHORTAGE";
        sql.update("UPDATE pick_item SET actual_qty=?, shortage_qty=?, status=?, remark=? WHERE id=?",
                newActual, shortage, status, BizNumbers.str(body.get("remark")), itemId);

        String waveNo = BizNumbers.str(item.get("waveNo"));
        String loc = BizNumbers.str(item.get("locationCode"));
        String sku = BizNumbers.str(item.get("skuCode"));
        String reason = body.get("reason") == null ? "STOCK_ZERO" : BizNumbers.str(body.get("reason"));
        String firstOrder = firstOrder(BizNumbers.str(item.get("relatedOrderNos")));

        Map<String, Object> exist = sql.one(
                "SELECT * FROM shortage WHERE task_no=? AND sku_code=? AND location_code=? AND status='PENDING'",
                taskNo, sku, loc);
        if (exist != null) {
            sql.update("UPDATE shortage SET shortage_qty=?, reason=?, report_time=? WHERE id=?",
                    shortage, reason, BizNumbers.now(), exist.get("id"));
        } else {
            sql.update("INSERT INTO shortage (shortage_no,wave_no,task_no,order_no,location_code,sku_code," +
                            "sku_name,should_qty,shortage_qty,reason,reporter_no,reporter_name,report_time,status,create_time) " +
                            "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?, 'PENDING', ?)",
                    bizNumbers.nextShortageNo(), waveNo, taskNo, firstOrder, loc, sku, item.get("skuName"),
                    should, shortage, reason,
                    me.employeeNo(), me.employeeName(),
                    BizNumbers.now(), BizNumbers.now());
        }

        sql.update("UPDATE pick_task SET status='EXCEPTION' WHERE task_no=? AND status IN ('WAITING','PICKING','EXCEPTION')", taskNo);
        if (waveNo != null) sql.update("UPDATE wave SET has_shortage=1 WHERE wave_no=?", waveNo);
        recalcTask(taskNo);
        operationLogService.log("SHORTAGE", taskNo, "PICK_SHORTAGE", "上报缺货", me.employeeNo(), me.employeeName(), me.role(),
                "库位=" + loc + " SKU=" + sku + " 缺货=" + shortage);
        return voAssembler.pickTaskVo(taskNo);
    }

    /**
     * 开始拣货。
     *
     * <p>只允许 WAITING → PICKING，已完成的单据不能回退（SEC-17）；
     * 拣货员固定为当前登录人，不接受请求参数指定他人（SEC-18）。</p>
     */
    public Map<String, Object> start(String taskNo) {
        LoginUser me = AuthContext.require();
        Map<String, Object> t = sql.one( "SELECT * FROM pick_task WHERE task_no=?", taskNo);
        if (t == null) throw new IllegalArgumentException("拣货任务不存在：" + taskNo);

        assertOwnership(t);
        assertStatus(t, "开始拣货", "WAITING");

        // 拣货员本人操作时以本人为准；管理角色代操作时保留原指派，不冒名
        String pickerNo;
        String pickerName;
        if (me.hasRole("PICKER")) {
            pickerNo = me.employeeNo();
            pickerName = me.employeeName();
        } else {
            pickerNo = BizNumbers.str(t.get("pickerNo"));
            pickerName = BizNumbers.str(t.get("pickerName"));
            if (pickerNo == null) {
                throw new IllegalStateException("该任务尚未指派拣货员，请先在波次管理里指派");
            }
        }

        String now = BizNumbers.now();
        int n = sql.update("UPDATE pick_task SET picker_no=?, picker_name=?, status='PICKING', start_time=? " +
                        "WHERE task_no=? AND status='WAITING'",
                pickerNo, pickerName, now, taskNo);
        if (n == 0) {
            throw new IllegalStateException("该任务已被他人开始，请刷新后重试");
        }
        sql.update("UPDATE wave SET status='PICKING', pick_start_time=? " +
                        "WHERE wave_no=? AND status IN ('CREATED','ASSIGNED')",
                now, BizNumbers.str(t.get("waveNo")));
        operationLogService.log("PICK", taskNo, "PICK_START", "开始拣货", me.employeeNo(), me.employeeName(), me.role(), null);
        return voAssembler.pickTaskVo(taskNo);
    }

    /**
     * 完成拣货：只允许 PICKING / EXCEPTION → 终态，不能从 WAITING 直接跳到完成（SEC-17）。
     */
    public Map<String, Object> finish(String taskNo) {
        LoginUser me = AuthContext.require();
        Map<String, Object> t = sql.one( "SELECT * FROM pick_task WHERE task_no=?", taskNo);
        if (t == null) throw new IllegalArgumentException("拣货任务不存在：" + taskNo);

        assertOwnership(t);
        assertStatus(t, "完成拣货", "PICKING", "EXCEPTION");

        String now = BizNumbers.now();
        int duration = secondsBetween(BizNumbers.str(t.get("startTime")), now);
        int shortage = BizNumbers.i(t.get("shortageQty"));
        String status = shortage > 0 ? "EXCEPTION" : "FINISHED";
        int n = sql.update("UPDATE pick_task SET status=?, finish_time=?, duration_sec=? " +
                        "WHERE task_no=? AND status IN ('PICKING','EXCEPTION')",
                status, now, duration, taskNo);
        if (n == 0) {
            throw new IllegalStateException("该任务状态已变化，请刷新后重试");
        }
        String waveNo = BizNumbers.str(t.get("waveNo"));
        if (waveNo != null) {
            sql.update("UPDATE wave SET status='PICKED', pick_finish_time=?, pick_duration_sec=? " +
                            "WHERE wave_no=? AND status='PICKING'",
                    now, duration, waveNo);
            sql.update("UPDATE `order` SET status='PICKED' WHERE wave_no=? AND status='WAVED'", waveNo);
        }
        operationLogService.log("PICK", taskNo, "PICK_FINISH", "拣货完成", me.employeeNo(), me.employeeName(), me.role(),
                "应拣 " + BizNumbers.i(t.get("shouldQty")) + " 件，实拣 " + BizNumbers.i(t.get("actualQty")) + " 件，缺货 " + shortage + " 件");
        return voAssembler.pickTaskVo(taskNo);
    }

    public PageResult<Map<String, Object>> page(
            String taskNo,
            String waveNo,
            String pickerNo,
            String status,
            int pageNum,
            int pageSize) {

        LoginUser me = AuthContext.require();
        // 拣货员只看到自己的任务；管理角色可按工号筛选
        String scope = me.hasRole("ADMIN", "WAVE_OPERATOR")
                ? (notBlank(pickerNo) ? pickerNo.trim() : null)
                : me.employeeNo();

        StringBuilder q = new StringBuilder("SELECT * FROM pick_task WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (notBlank(taskNo)) { q.append(" AND task_no LIKE ?"); args.add("%" + taskNo.trim() + "%"); }
        if (notBlank(waveNo)) { q.append(" AND wave_no=?"); args.add(waveNo); }
        if (scope != null) { q.append(" AND picker_no=?"); args.add(scope); }
        if (notBlank(status)) { q.append(" AND status=?"); args.add(status); }
        q.append(" ORDER BY create_time DESC, id DESC");

        Map<String, Object> pg = this.sql.page(q.toString(), pageNum, pageSize, args.toArray());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) pg.get("records");
        for (Map<String, Object> t : rows) {
            voAssembler.enrichTask(t);
            t.put("items", sql.list(
                    "SELECT * FROM pick_item WHERE task_no=? ORDER BY seq_no", BizNumbers.str(t.get("taskNo"))));
        }
        return new PageResult<>(rows, ((Number) pg.get("total")).longValue(), pageNum, pageSize);
    }

    public Map<String, Object> detail(String taskNo) {
        Map<String, Object> t = voAssembler.pickTaskVo(taskNo);
        if (t == null) throw new IllegalArgumentException("拣货任务不存在：" + taskNo);
        assertOwnership(t);
        return t;
    }

    // ---------------- 内部 ----------------

    /** 取任务并校验：状态可操作 + 归属本人 */
    private Map<String, Object> requireOperableTask(String taskNo) {
        Map<String, Object> task = sql.one( "SELECT * FROM pick_task WHERE task_no=?", taskNo);
        if (task == null) throw new IllegalArgumentException("拣货任务不存在：" + taskNo);
        assertOwnership(task);
        assertStatus(task, "修改拣货明细", "WAITING", "PICKING", "EXCEPTION");
        return task;
    }

    /** 拣货员只能操作指派给自己的任务；管理角色不受限 */
    private void assertOwnership(Map<String, Object> task) {
        LoginUser me = AuthContext.require();
        if (me.hasRole("ADMIN", "WAVE_OPERATOR")) {
            return;
        }
        String owner = BizNumbers.str(task.get("pickerNo"));
        if (owner != null && !owner.equals(me.employeeNo())) {
            throw new ForbiddenException("该任务指派给 " + BizNumbers.str(task.get("pickerName")) + "，你无权操作");
        }
    }

    /** 单据状态机前置校验，避免状态回退与跳步（SEC-17） */
    private void assertStatus(Map<String, Object> task, String action, String... allowed) {
        String cur = BizNumbers.str(task.get("status"));
        for (String a : allowed) {
            if (a.equals(cur)) return;
        }
        throw new IllegalStateException("任务当前状态为「" + Dict.desc("pickTaskStatus", cur)
                + "」，不能" + action);
    }

    private void recalcTask(String taskNo) {
        if (taskNo == null) return;
        Map<String, Object> agg = sql.one(
                "SELECT SUM(CASE WHEN status='PICKED' THEN 1 ELSE 0 END) finished, " +
                        "COALESCE(SUM(actual_qty),0) actual, COALESCE(SUM(shortage_qty),0) shortage " +
                        "FROM pick_item WHERE task_no=?", taskNo);
        int finished = agg == null ? 0 : BizNumbers.i(agg.get("finished"));
        int actual = agg == null ? 0 : BizNumbers.i(agg.get("actual"));
        int shortage = agg == null ? 0 : BizNumbers.i(agg.get("shortage"));
        sql.update("UPDATE pick_task SET finished_count=?, actual_qty=?, shortage_qty=? WHERE task_no=?",
                finished, actual, shortage, taskNo);
    }

    private static String firstOrder(String related) {
        if (related == null || related.isBlank()) return null;
        int i = related.indexOf(',');
        return i < 0 ? related : related.substring(0, i);
    }

    private static int secondsBetween(String start, String end) {
        if (start == null || start.isBlank()) return 0;
        try {
            SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            return (int) Math.max(0, (f.parse(end).getTime() - f.parse(start).getTime()) / 1000);
        } catch (Exception e) {
            return 0;
        }
    }

    private static long longVal(Object o) {
        if (o == null) return 0;
        if (o instanceof Number n) return n.longValue();
        try {
            return Long.parseLong(String.valueOf(o));
        } catch (Exception e) {
            return 0;
        }
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}


