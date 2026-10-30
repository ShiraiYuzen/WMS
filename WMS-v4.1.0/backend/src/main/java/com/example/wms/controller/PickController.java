package com.example.wms.controller;

import com.example.wms.common.AuthContext;
import com.example.wms.common.Dict;
import com.example.wms.common.ForbiddenException;
import com.example.wms.common.JdbcMaps;
import com.example.wms.common.LoginUser;
import com.example.wms.common.RequireRole;
import com.example.wms.dto.ApiResult;
import com.example.wms.dto.PageResult;
import com.example.wms.service.Repo;
import org.springframework.web.bind.annotation.*;

import java.text.SimpleDateFormat;
import java.util.*;

@RestController
@RequestMapping("/api/pick")
public class PickController {

    private final Repo repo;

    public PickController(Repo repo) {
        this.repo = repo;
    }

    /**
     * 我的拣货任务。
     *
     * <p>拣货员只能看自己的（忽略传入的 pickerNo，防越权查看，SEC-20）；
     * 主管/波次员可按工号筛选，不传则看全部。</p>
     */
    @GetMapping("/my-tasks")
    public ApiResult<List<Map<String, Object>>> myTasks(@RequestParam(required = false) String pickerNo) {
        LoginUser me = AuthContext.require();
        String scope = me.hasRole("ADMIN", "WAVE_OPERATOR")
                ? (notBlank(pickerNo) ? pickerNo.trim() : null)
                : me.employeeNo();

        List<Map<String, Object>> rows;
        if (scope != null) {
            rows = JdbcMaps.list(repo.jdbc(),
                    "SELECT * FROM pick_task WHERE picker_no=? ORDER BY create_time DESC, id DESC", scope);
        } else {
            rows = JdbcMaps.list(repo.jdbc(),
                    "SELECT * FROM pick_task ORDER BY create_time DESC, id DESC");
        }
        for (Map<String, Object> t : rows) {
            repo.enrichTask(t);
            t.put("items", JdbcMaps.list(repo.jdbc(),
                    "SELECT * FROM pick_item WHERE task_no=? ORDER BY seq_no", Repo.str(t.get("taskNo"))));
        }
        return ApiResult.ok(rows);
    }

    @RequireRole({"ADMIN", "PICKER", "WAVE_OPERATOR"})
    @PostMapping("/confirm-item")
    public ApiResult<Map<String, Object>> confirmItem(@RequestBody Map<String, Object> body) {
        long itemId = longVal(body.get("itemId"));
        Map<String, Object> item = JdbcMaps.one(repo.jdbc(), "SELECT * FROM pick_item WHERE id=?", itemId);
        if (item == null) throw new IllegalArgumentException("拣货明细不存在");

        String taskNo = Repo.str(item.get("taskNo"));
        Map<String, Object> task = requireOperableTask(taskNo);

        int should = Repo.i(item.get("shouldQty"));
        int actual = body.get("actualQty") == null ? should : Repo.i(body.get("actualQty"));
        if (actual < 0) throw new IllegalArgumentException("实拣数量不能为负数");
        if (actual > should) {
            throw new IllegalArgumentException("实拣数量 " + actual + " 超过应拣数量 " + should + "，多货请走复核差异登记");
        }
        String status = actual >= should ? "PICKED" : (actual > 0 ? "PARTIAL" : "PENDING");
        repo.jdbc().update("UPDATE pick_item SET actual_qty=?, status=?, scan_time=? WHERE id=?",
                actual, status, Repo.now(), itemId);
        recalcTask(taskNo);
        return ApiResult.ok(repo.pickTaskVo(Repo.str(task.get("taskNo"))));
    }

    @RequireRole({"ADMIN", "PICKER", "WAVE_OPERATOR"})
    @PostMapping("/report-shortage")
    public ApiResult<Map<String, Object>> reportShortage(@RequestBody Map<String, Object> body) {
        LoginUser me = AuthContext.require();
        long itemId = longVal(body.get("itemId"));
        Map<String, Object> item = JdbcMaps.one(repo.jdbc(), "SELECT * FROM pick_item WHERE id=?", itemId);
        if (item == null) throw new IllegalArgumentException("拣货明细不存在");

        String taskNo = Repo.str(item.get("taskNo"));
        Map<String, Object> task = requireOperableTask(taskNo);

        int should = Repo.i(item.get("shouldQty"));
        int actual = Repo.i(item.get("actualQty"));
        int remain = Math.max(1, should - actual);
        int shortage = body.get("shortageQty") == null ? remain : Repo.i(body.get("shortageQty"));
        if (shortage <= 0) shortage = remain;
        // 缺货量必须落在 1 ~ 未拣数量之间，防止上报超过应拣量（SEC-19）
        if (shortage > should - actual) {
            throw new IllegalArgumentException("缺货数量 " + shortage + " 超过未拣数量 " + (should - actual));
        }
        int newActual = Math.max(0, should - shortage);
        String status = newActual > 0 ? "PARTIAL" : "SHORTAGE";
        repo.jdbc().update("UPDATE pick_item SET actual_qty=?, shortage_qty=?, status=?, remark=? WHERE id=?",
                newActual, shortage, status, Repo.str(body.get("remark")), itemId);

        String waveNo = Repo.str(item.get("waveNo"));
        String loc = Repo.str(item.get("locationCode"));
        String sku = Repo.str(item.get("skuCode"));
        String reason = body.get("reason") == null ? "STOCK_ZERO" : Repo.str(body.get("reason"));
        String firstOrder = firstOrder(Repo.str(item.get("relatedOrderNos")));

        Map<String, Object> exist = JdbcMaps.one(repo.jdbc(),
                "SELECT * FROM shortage WHERE task_no=? AND sku_code=? AND location_code=? AND status='PENDING'",
                taskNo, sku, loc);
        if (exist != null) {
            repo.jdbc().update("UPDATE shortage SET shortage_qty=?, reason=?, report_time=? WHERE id=?",
                    shortage, reason, Repo.now(), exist.get("id"));
        } else {
            repo.jdbc().update("INSERT INTO shortage (shortage_no,wave_no,task_no,order_no,location_code,sku_code," +
                            "sku_name,should_qty,shortage_qty,reason,reporter_no,reporter_name,report_time,status,create_time) " +
                            "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?, 'PENDING', ?)",
                    repo.nextShortageNo(), waveNo, taskNo, firstOrder, loc, sku, item.get("skuName"),
                    should, shortage, reason,
                    me.employeeNo(), me.employeeName(),
                    Repo.now(), Repo.now());
        }

        repo.jdbc().update("UPDATE pick_task SET status='EXCEPTION' WHERE task_no=? AND status IN ('WAITING','PICKING','EXCEPTION')", taskNo);
        if (waveNo != null) repo.jdbc().update("UPDATE wave SET has_shortage=1 WHERE wave_no=?", waveNo);
        recalcTask(taskNo);
        repo.log("SHORTAGE", taskNo, "PICK_SHORTAGE", "上报缺货", me.employeeNo(), me.employeeName(), me.role(),
                "库位=" + loc + " SKU=" + sku + " 缺货=" + shortage);
        return ApiResult.ok(repo.pickTaskVo(taskNo));
    }

    /**
     * 开始拣货。
     *
     * <p>只允许 WAITING → PICKING，已完成的单据不能回退（SEC-17）；
     * 拣货员固定为当前登录人，不接受请求参数指定他人（SEC-18）。</p>
     */
    @RequireRole({"ADMIN", "PICKER", "WAVE_OPERATOR"})
    @PostMapping("/{taskNo}/start")
    public ApiResult<Map<String, Object>> start(@PathVariable String taskNo) {
        LoginUser me = AuthContext.require();
        Map<String, Object> t = JdbcMaps.one(repo.jdbc(), "SELECT * FROM pick_task WHERE task_no=?", taskNo);
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
            pickerNo = Repo.str(t.get("pickerNo"));
            pickerName = Repo.str(t.get("pickerName"));
            if (pickerNo == null) {
                throw new IllegalStateException("该任务尚未指派拣货员，请先在波次管理里指派");
            }
        }

        String now = Repo.now();
        int n = repo.jdbc().update("UPDATE pick_task SET picker_no=?, picker_name=?, status='PICKING', start_time=? " +
                        "WHERE task_no=? AND status='WAITING'",
                pickerNo, pickerName, now, taskNo);
        if (n == 0) {
            throw new IllegalStateException("该任务已被他人开始，请刷新后重试");
        }
        repo.jdbc().update("UPDATE wave SET status='PICKING', pick_start_time=? " +
                        "WHERE wave_no=? AND status IN ('CREATED','ASSIGNED')",
                now, Repo.str(t.get("waveNo")));
        repo.log("PICK", taskNo, "PICK_START", "开始拣货", me.employeeNo(), me.employeeName(), me.role(), null);
        return ApiResult.ok(repo.pickTaskVo(taskNo));
    }

    /**
     * 完成拣货：只允许 PICKING / EXCEPTION → 终态，不能从 WAITING 直接跳到完成（SEC-17）。
     */
    @RequireRole({"ADMIN", "PICKER", "WAVE_OPERATOR"})
    @PostMapping("/{taskNo}/finish")
    public ApiResult<Map<String, Object>> finish(@PathVariable String taskNo) {
        LoginUser me = AuthContext.require();
        Map<String, Object> t = JdbcMaps.one(repo.jdbc(), "SELECT * FROM pick_task WHERE task_no=?", taskNo);
        if (t == null) throw new IllegalArgumentException("拣货任务不存在：" + taskNo);

        assertOwnership(t);
        assertStatus(t, "完成拣货", "PICKING", "EXCEPTION");

        String now = Repo.now();
        int duration = secondsBetween(Repo.str(t.get("startTime")), now);
        int shortage = Repo.i(t.get("shortageQty"));
        String status = shortage > 0 ? "EXCEPTION" : "FINISHED";
        int n = repo.jdbc().update("UPDATE pick_task SET status=?, finish_time=?, duration_sec=? " +
                        "WHERE task_no=? AND status IN ('PICKING','EXCEPTION')",
                status, now, duration, taskNo);
        if (n == 0) {
            throw new IllegalStateException("该任务状态已变化，请刷新后重试");
        }
        String waveNo = Repo.str(t.get("waveNo"));
        if (waveNo != null) {
            repo.jdbc().update("UPDATE wave SET status='PICKED', pick_finish_time=?, pick_duration_sec=? " +
                            "WHERE wave_no=? AND status='PICKING'",
                    now, duration, waveNo);
            repo.jdbc().update("UPDATE `order` SET status='PICKED' WHERE wave_no=? AND status='WAVED'", waveNo);
        }
        repo.log("PICK", taskNo, "PICK_FINISH", "拣货完成", me.employeeNo(), me.employeeName(), me.role(),
                "应拣 " + Repo.i(t.get("shouldQty")) + " 件，实拣 " + Repo.i(t.get("actualQty")) + " 件，缺货 " + shortage + " 件");
        return ApiResult.ok(repo.pickTaskVo(taskNo));
    }

    @GetMapping("/page")
    public ApiResult<PageResult<Map<String, Object>>> page(
            @RequestParam(required = false) String taskNo,
            @RequestParam(required = false) String waveNo,
            @RequestParam(required = false) String pickerNo,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {

        LoginUser me = AuthContext.require();
        // 拣货员只看到自己的任务；管理角色可按工号筛选
        String scope = me.hasRole("ADMIN", "WAVE_OPERATOR")
                ? (notBlank(pickerNo) ? pickerNo.trim() : null)
                : me.employeeNo();

        StringBuilder sql = new StringBuilder("SELECT * FROM pick_task WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (notBlank(taskNo)) { sql.append(" AND task_no LIKE ?"); args.add("%" + taskNo.trim() + "%"); }
        if (notBlank(waveNo)) { sql.append(" AND wave_no=?"); args.add(waveNo); }
        if (scope != null) { sql.append(" AND picker_no=?"); args.add(scope); }
        if (notBlank(status)) { sql.append(" AND status=?"); args.add(status); }
        sql.append(" ORDER BY create_time DESC, id DESC");

        Map<String, Object> pg = JdbcMaps.page(repo.jdbc(), sql.toString(), pageNum, pageSize, args.toArray());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) pg.get("records");
        for (Map<String, Object> t : rows) {
            repo.enrichTask(t);
            t.put("items", JdbcMaps.list(repo.jdbc(),
                    "SELECT * FROM pick_item WHERE task_no=? ORDER BY seq_no", Repo.str(t.get("taskNo"))));
        }
        return ApiResult.ok(new PageResult<>(rows, ((Number) pg.get("total")).longValue(), pageNum, pageSize));
    }

    @GetMapping("/{taskNo}")
    public ApiResult<Map<String, Object>> detail(@PathVariable String taskNo) {
        Map<String, Object> t = repo.pickTaskVo(taskNo);
        if (t == null) throw new IllegalArgumentException("拣货任务不存在：" + taskNo);
        assertOwnership(t);
        return ApiResult.ok(t);
    }

    // ---------------- 内部 ----------------

    /** 取任务并校验：状态可操作 + 归属本人 */
    private Map<String, Object> requireOperableTask(String taskNo) {
        Map<String, Object> task = JdbcMaps.one(repo.jdbc(), "SELECT * FROM pick_task WHERE task_no=?", taskNo);
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
        String owner = Repo.str(task.get("pickerNo"));
        if (owner != null && !owner.equals(me.employeeNo())) {
            throw new ForbiddenException("该任务指派给 " + Repo.str(task.get("pickerName")) + "，你无权操作");
        }
    }

    /** 单据状态机前置校验，避免状态回退与跳步（SEC-17） */
    private void assertStatus(Map<String, Object> task, String action, String... allowed) {
        String cur = Repo.str(task.get("status"));
        for (String a : allowed) {
            if (a.equals(cur)) return;
        }
        throw new IllegalStateException("任务当前状态为「" + Dict.desc("pickTaskStatus", cur)
                + "」，不能" + action);
    }

    private void recalcTask(String taskNo) {
        if (taskNo == null) return;
        Map<String, Object> agg = JdbcMaps.one(repo.jdbc(),
                "SELECT SUM(CASE WHEN status='PICKED' THEN 1 ELSE 0 END) finished, " +
                        "COALESCE(SUM(actual_qty),0) actual, COALESCE(SUM(shortage_qty),0) shortage " +
                        "FROM pick_item WHERE task_no=?", taskNo);
        int finished = agg == null ? 0 : Repo.i(agg.get("finished"));
        int actual = agg == null ? 0 : Repo.i(agg.get("actual"));
        int shortage = agg == null ? 0 : Repo.i(agg.get("shortage"));
        repo.jdbc().update("UPDATE pick_task SET finished_count=?, actual_qty=?, shortage_qty=? WHERE task_no=?",
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
