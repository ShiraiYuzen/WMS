package com.example.wms.controller;

import com.example.wms.common.AuthContext;
import com.example.wms.common.Dict;
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
@RequestMapping("/api/review")
public class ReviewController {

    private final Repo repo;

    public ReviewController(Repo repo) {
        this.repo = repo;
    }

    /** 待复核波次：这是复核岗的工作队列，只对复核员与主管开放 */
    @RequireRole({"ADMIN", "REVIEWER"})
    @GetMapping("/pending-waves")
    public ApiResult<List<Map<String, Object>>> pendingWaves() {
        List<Map<String, Object>> waves = JdbcMaps.list(repo.jdbc(),
                "SELECT * FROM wave WHERE status IN ('PICKED','REVIEWING') ORDER BY pick_finish_time ASC, id ASC");
        for (Map<String, Object> w : waves) repo.enrichWave(w);
        return ApiResult.ok(waves);
    }

    /** 领取复核：复核人恒为当前登录人，不再接受 reviewerNo 参数（SEC-18） */
    @RequireRole({"ADMIN", "REVIEWER"})
    @PostMapping("/claim")
    public ApiResult<List<Map<String, Object>>> claim(@RequestParam String waveNo) {
        LoginUser me = AuthContext.require();
        Map<String, Object> w = JdbcMaps.one(repo.jdbc(), "SELECT * FROM wave WHERE wave_no=?", waveNo);
        if (w == null) throw new IllegalArgumentException("波次不存在：" + waveNo);
        String st = Repo.str(w.get("status"));
        if (!"PICKED".equals(st) && !"REVIEWING".equals(st)) {
            throw new IllegalStateException("波次当前状态（" + Dict.desc("waveStatus", st) + "）不可复核");
        }
        String reviewerNo = me.employeeNo();
        String name = me.employeeName() != null ? me.employeeName() : repo.employeeName(reviewerNo);

        List<Map<String, Object>> orders = JdbcMaps.list(repo.jdbc(),
                "SELECT o.* FROM `order` o JOIN wave_order wo ON wo.order_no=o.order_no " +
                        "WHERE wo.wave_no=? ORDER BY wo.seq_no", waveNo);
        String now = Repo.now();
        for (Map<String, Object> o : orders) {
            String orderNo = Repo.str(o.get("orderNo"));
            Map<String, Object> exist = JdbcMaps.one(repo.jdbc(),
                    "SELECT * FROM review WHERE wave_no=? AND order_no=?", waveNo, orderNo);
            if (exist == null) {
                repo.jdbc().update("INSERT INTO review (review_no,wave_no,order_no,reviewer_no,reviewer_name,status," +
                                "should_sku_count,should_qty,actual_qty,diff_type,diff_qty,start_time,create_time) " +
                                "VALUES (?,?,?,?,?, 'WAITING', ?,?,?, 'NONE', 0, ?, ?)",
                        repo.nextReviewNo(), waveNo, orderNo, reviewerNo, name,
                        Repo.i(o.get("skuCount")), Repo.i(o.get("totalQty")), Repo.i(o.get("totalQty")), now, now);
            }
        }
        repo.jdbc().update("UPDATE wave SET status='REVIEWING', reviewer_no=?, reviewer_name=? WHERE wave_no=?",
                reviewerNo, name, waveNo);
        repo.log("REVIEW", waveNo, "REVIEW_CLAIM", "领取复核", reviewerNo, name, me.role(),
                "复核订单数=" + orders.size());

        List<Map<String, Object>> reviews = JdbcMaps.list(repo.jdbc(),
                "SELECT review_no FROM review WHERE wave_no=? ORDER BY id", waveNo);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> r : reviews) {
            result.add(repo.reviewVo(Repo.str(r.get("reviewNo"))));
        }
        return ApiResult.ok(result);
    }

    /**
     * 提交复核。
     *
     * <p>只允许 WAITING → PASS/DIFF，已出结果的单据拒绝再次提交，
     * 否则可以用后一次提交覆盖前一次结论（SEC-15）；
     * 复核人恒为当前登录人（SEC-18）。</p>
     */
    @RequireRole({"ADMIN", "REVIEWER"})
    @PostMapping("/submit")
    public ApiResult<Map<String, Object>> submit(@RequestBody Map<String, Object> body) {
        LoginUser me = AuthContext.require();
        String reviewNo = Repo.str(body.get("reviewNo"));
        Map<String, Object> r = JdbcMaps.one(repo.jdbc(), "SELECT * FROM review WHERE review_no=?", reviewNo);
        if (r == null) throw new IllegalArgumentException("复核单不存在：" + reviewNo);

        String curStatus = Repo.str(r.get("status"));
        if (!"WAITING".equals(curStatus)) {
            throw new IllegalStateException("该复核单已是「" + Dict.desc("reviewStatus", curStatus)
                    + "」，不能重复提交；如需更正请走差异处理流程");
        }

        String reviewerNo = me.employeeNo();
        String reviewerName = me.employeeName() != null ? me.employeeName() : Repo.str(r.get("reviewerName"));
        int shouldQty = Repo.i(r.get("shouldQty"));
        int actualQty = body.get("actualQty") == null ? shouldQty : Repo.i(body.get("actualQty"));
        if (actualQty < 0) throw new IllegalArgumentException("实盘数量不能为负数");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> diffItems = body.get("diffItems") instanceof List<?> l
                ? (List<Map<String, Object>>) l : Collections.emptyList();

        String now = Repo.now();
        int duration = secondsBetween(Repo.str(r.get("startTime")), now);

        String status;
        String diffType;
        int diffQty = 0;
        String diffDetail = null;

        if (diffItems.isEmpty()) {
            status = "PASS";
            diffType = "NONE";
        } else {
            status = "DIFF";
            diffType = Repo.str(diffItems.get(0).get("diffType"));
            for (Map<String, Object> d : diffItems) {
                int dq = d.get("diffQty") != null ? Repo.i(d.get("diffQty"))
                        : Repo.i(d.get("shouldQty")) - Repo.i(d.get("actualQty"));
                diffQty += dq;
            }
            diffDetail = "差异 " + diffItems.size() + " 项，合计 " + diffQty + " 件";
        }

        // 条件更新：状态必须是 WAITING，天然防止并发/重复提交
        int n = repo.jdbc().update("UPDATE review SET reviewer_no=?, reviewer_name=?, status=?, actual_qty=?, diff_type=?, " +
                        "diff_qty=?, diff_detail=?, pack_material=?, package_count=?, weight=?, finish_time=?, " +
                        "duration_sec=?, remark=? WHERE review_no=? AND status='WAITING'",
                reviewerNo, reviewerName, status, actualQty, diffType, diffQty, diffDetail,
                Repo.str(body.get("packMaterial")),
                body.get("packageCount") == null ? null : Repo.i(body.get("packageCount")),
                body.get("weight") == null ? null : Repo.d(body.get("weight")),
                now, duration, Repo.str(body.get("remark")), reviewNo);
        if (n == 0) {
            throw new IllegalStateException("该复核单已被其他人提交，请刷新后查看");
        }

        // 状态迁移成功后再落差异明细，避免"单据没提交成功却留下差异行"
        for (Map<String, Object> d : diffItems) {
            int dq = d.get("diffQty") != null ? Repo.i(d.get("diffQty"))
                    : Repo.i(d.get("shouldQty")) - Repo.i(d.get("actualQty"));
            repo.jdbc().update("INSERT INTO review_diff (review_no,wave_no,order_no,sku_code,sku_name,diff_type," +
                            "should_qty,actual_qty,diff_qty,responsible_stage,responsible_no,remark,create_time) " +
                            "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)",
                    reviewNo, Repo.str(r.get("waveNo")), Repo.str(r.get("orderNo")),
                    Repo.str(d.get("skuCode")), Repo.str(d.get("skuName")), Repo.str(d.get("diffType")),
                    Repo.i(d.get("shouldQty")), Repo.i(d.get("actualQty")), dq,
                    Repo.str(d.get("responsibleStage")), Repo.str(d.get("responsibleNo")),
                    Repo.str(d.get("remark")), now);
        }

        String waveNo = Repo.str(r.get("waveNo"));
        finishWaveIfDone(waveNo, now, me);

        repo.log("REVIEW", reviewNo, "REVIEW_SUBMIT", "提交复核", reviewerNo, reviewerName, me.role(),
                "结果=" + Dict.desc("reviewStatus", status) + (diffQty != 0 ? "，差异=" + diffQty + " 件" : ""));
        return ApiResult.ok(repo.reviewVo(reviewNo));
    }

    @GetMapping("/page")
    public ApiResult<PageResult<Map<String, Object>>> page(
            @RequestParam(required = false) String reviewNo,
            @RequestParam(required = false) String waveNo,
            @RequestParam(required = false) String orderNo,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String diffType,
            @RequestParam(required = false) String reviewerNo,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {

        LoginUser me = AuthContext.require();
        // 复核员只看自己名下的复核单；主管/波次员可按工号筛选或看全量
        String scope = me.hasRole("ADMIN", "WAVE_OPERATOR")
                ? (notBlank(reviewerNo) ? reviewerNo.trim() : null)
                : me.employeeNo();

        StringBuilder sql = new StringBuilder("SELECT * FROM review WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (notBlank(reviewNo)) { sql.append(" AND review_no LIKE ?"); args.add("%" + reviewNo.trim() + "%"); }
        if (notBlank(waveNo)) { sql.append(" AND wave_no=?"); args.add(waveNo); }
        if (notBlank(orderNo)) { sql.append(" AND order_no=?"); args.add(orderNo); }
        if (notBlank(status)) { sql.append(" AND status=?"); args.add(status); }
        if (notBlank(diffType)) { sql.append(" AND diff_type=?"); args.add(diffType); }
        if (scope != null) { sql.append(" AND reviewer_no=?"); args.add(scope); }
        sql.append(" ORDER BY create_time DESC, id DESC");

        Map<String, Object> pg = JdbcMaps.page(repo.jdbc(), sql.toString(), pageNum, pageSize, args.toArray());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) pg.get("records");
        for (Map<String, Object> r : rows) {
            repo.enrichReview(r);
            r.put("shouldItems", JdbcMaps.list(repo.jdbc(),
                    "SELECT * FROM order_item WHERE order_no=? ORDER BY line_no", Repo.str(r.get("orderNo"))));
            r.put("diffItems", JdbcMaps.list(repo.jdbc(),
                    "SELECT * FROM review_diff WHERE review_no=? ORDER BY id", Repo.str(r.get("reviewNo"))));
        }
        return ApiResult.ok(new PageResult<>(rows, ((Number) pg.get("total")).longValue(), pageNum, pageSize));
    }

    @GetMapping("/{reviewNo}")
    public ApiResult<Map<String, Object>> detail(@PathVariable String reviewNo) {
        Map<String, Object> r = repo.reviewVo(reviewNo);
        if (r == null) throw new IllegalArgumentException("复核单不存在：" + reviewNo);
        return ApiResult.ok(r);
    }

    // ---------------- 内部 ----------------

    private void finishWaveIfDone(String waveNo, String now, LoginUser me) {
        if (waveNo == null) return;
        long waiting = JdbcMaps.count(repo.jdbc(),
                "SELECT COUNT(*) FROM review WHERE wave_no=? AND status='WAITING'", waveNo);
        if (waiting == 0) {
            repo.jdbc().update("UPDATE wave SET status='REVIEWED', review_finish_time=? WHERE wave_no=?", now, waveNo);
            repo.jdbc().update("UPDATE `order` SET status='REVIEWED' WHERE wave_no=? AND status='PICKED'", waveNo);
            repo.log("REVIEW", waveNo, "REVIEW_FINISH", "复核完成", me.employeeNo(), me.employeeName(), me.role(),
                    "波次复核完成");
        }
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

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
