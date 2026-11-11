package com.example.wms.service;

import com.example.wms.common.AuthContext;
import com.example.wms.common.Dict;
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
public class ReviewService {

    private final SqlSupport sql;
    private final BizNumbers bizNumbers;
    private final VoAssembler voAssembler;
    private final OperationLogService operationLogService;

    public ReviewService(SqlSupport sql, BizNumbers bizNumbers, VoAssembler voAssembler, OperationLogService operationLogService) {
        this.sql = sql;
        this.bizNumbers = bizNumbers;
        this.voAssembler = voAssembler;
        this.operationLogService = operationLogService;
    }
    /** 待复核波次：这是复核岗的工作队列，只对复核员与主管开放 */
    public List<Map<String, Object>> pendingWaves() {
        List<Map<String, Object>> waves = sql.list(
                "SELECT * FROM wave WHERE status IN ('PICKED','REVIEWING') ORDER BY pick_finish_time ASC, id ASC");
        for (Map<String, Object> w : waves) voAssembler.enrichWave(w);
        return waves;
    }

    /** 领取复核：复核人恒为当前登录人，不再接受 reviewerNo 参数（SEC-18） */
    public List<Map<String, Object>> claim(String waveNo) {
        LoginUser me = AuthContext.require();
        Map<String, Object> w = sql.one( "SELECT * FROM wave WHERE wave_no=?", waveNo);
        if (w == null) throw new IllegalArgumentException("波次不存在：" + waveNo);
        String st = BizNumbers.str(w.get("status"));
        if (!"PICKED".equals(st) && !"REVIEWING".equals(st)) {
            throw new IllegalStateException("波次当前状态（" + Dict.desc("waveStatus", st) + "）不可复核");
        }
        String reviewerNo = me.employeeNo();
        String name = me.employeeName() != null ? me.employeeName() : voAssembler.employeeName(reviewerNo);

        List<Map<String, Object>> orders = sql.list(
                "SELECT o.* FROM `order` o JOIN wave_order wo ON wo.order_no=o.order_no " +
                        "WHERE wo.wave_no=? ORDER BY wo.seq_no", waveNo);
        String now = BizNumbers.now();
        for (Map<String, Object> o : orders) {
            String orderNo = BizNumbers.str(o.get("orderNo"));
            Map<String, Object> exist = sql.one(
                    "SELECT * FROM review WHERE wave_no=? AND order_no=?", waveNo, orderNo);
            if (exist == null) {
                sql.update("INSERT INTO review (review_no,wave_no,order_no,reviewer_no,reviewer_name,status," +
                                "should_sku_count,should_qty,actual_qty,diff_type,diff_qty,start_time,create_time) " +
                                "VALUES (?,?,?,?,?, 'WAITING', ?,?,?, 'NONE', 0, ?, ?)",
                        bizNumbers.nextReviewNo(), waveNo, orderNo, reviewerNo, name,
                        BizNumbers.i(o.get("skuCount")), BizNumbers.i(o.get("totalQty")), BizNumbers.i(o.get("totalQty")), now, now);
            }
        }
        sql.update("UPDATE wave SET status='REVIEWING', reviewer_no=?, reviewer_name=? WHERE wave_no=?",
                reviewerNo, name, waveNo);
        operationLogService.log("REVIEW", waveNo, "REVIEW_CLAIM", "领取复核", reviewerNo, name, me.role(),
                "复核订单数=" + orders.size());

        List<Map<String, Object>> reviews = sql.list(
                "SELECT review_no FROM review WHERE wave_no=? ORDER BY id", waveNo);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> r : reviews) {
            result.add(voAssembler.reviewVo(BizNumbers.str(r.get("reviewNo"))));
        }
        return result;
    }

    /**
     * 提交复核。
     *
     * <p>只允许 WAITING → PASS/DIFF，已出结果的单据拒绝再次提交，
     * 否则可以用后一次提交覆盖前一次结论（SEC-15）；
     * 复核人恒为当前登录人（SEC-18）。</p>
     */
    public Map<String, Object> submit(Map<String, Object> body) {
        LoginUser me = AuthContext.require();
        String reviewNo = BizNumbers.str(body.get("reviewNo"));
        Map<String, Object> r = sql.one( "SELECT * FROM review WHERE review_no=?", reviewNo);
        if (r == null) throw new IllegalArgumentException("复核单不存在：" + reviewNo);

        String curStatus = BizNumbers.str(r.get("status"));
        if (!"WAITING".equals(curStatus)) {
            throw new IllegalStateException("该复核单已是「" + Dict.desc("reviewStatus", curStatus)
                    + "」，不能重复提交；如需更正请走差异处理流程");
        }

        String reviewerNo = me.employeeNo();
        String reviewerName = me.employeeName() != null ? me.employeeName() : BizNumbers.str(r.get("reviewerName"));
        int shouldQty = BizNumbers.i(r.get("shouldQty"));
        int actualQty = body.get("actualQty") == null ? shouldQty : BizNumbers.i(body.get("actualQty"));
        if (actualQty < 0) throw new IllegalArgumentException("实盘数量不能为负数");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> diffItems = body.get("diffItems") instanceof List<?> l
                ? (List<Map<String, Object>>) l : Collections.emptyList();

        String now = BizNumbers.now();
        int duration = secondsBetween(BizNumbers.str(r.get("startTime")), now);

        String status;
        String diffType;
        int diffQty = 0;
        String diffDetail = null;

        if (diffItems.isEmpty()) {
            status = "PASS";
            diffType = "NONE";
        } else {
            status = "DIFF";
            diffType = BizNumbers.str(diffItems.get(0).get("diffType"));
            for (Map<String, Object> d : diffItems) {
                int dq = d.get("diffQty") != null ? BizNumbers.i(d.get("diffQty"))
                        : BizNumbers.i(d.get("shouldQty")) - BizNumbers.i(d.get("actualQty"));
                diffQty += dq;
            }
            diffDetail = "差异 " + diffItems.size() + " 项，合计 " + diffQty + " 件";
        }

        // 条件更新：状态必须是 WAITING，天然防止并发/重复提交
        int n = sql.update("UPDATE review SET reviewer_no=?, reviewer_name=?, status=?, actual_qty=?, diff_type=?, " +
                        "diff_qty=?, diff_detail=?, pack_material=?, package_count=?, weight=?, finish_time=?, " +
                        "duration_sec=?, remark=? WHERE review_no=? AND status='WAITING'",
                reviewerNo, reviewerName, status, actualQty, diffType, diffQty, diffDetail,
                BizNumbers.str(body.get("packMaterial")),
                body.get("packageCount") == null ? null : BizNumbers.i(body.get("packageCount")),
                body.get("weight") == null ? null : BizNumbers.d(body.get("weight")),
                now, duration, BizNumbers.str(body.get("remark")), reviewNo);
        if (n == 0) {
            throw new IllegalStateException("该复核单已被其他人提交，请刷新后查看");
        }

        // 状态迁移成功后再落差异明细，避免"单据没提交成功却留下差异行"
        for (Map<String, Object> d : diffItems) {
            int dq = d.get("diffQty") != null ? BizNumbers.i(d.get("diffQty"))
                    : BizNumbers.i(d.get("shouldQty")) - BizNumbers.i(d.get("actualQty"));
            sql.update("INSERT INTO review_diff (review_no,wave_no,order_no,sku_code,sku_name,diff_type," +
                            "should_qty,actual_qty,diff_qty,responsible_stage,responsible_no,remark,create_time) " +
                            "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)",
                    reviewNo, BizNumbers.str(r.get("waveNo")), BizNumbers.str(r.get("orderNo")),
                    BizNumbers.str(d.get("skuCode")), BizNumbers.str(d.get("skuName")), BizNumbers.str(d.get("diffType")),
                    BizNumbers.i(d.get("shouldQty")), BizNumbers.i(d.get("actualQty")), dq,
                    BizNumbers.str(d.get("responsibleStage")), BizNumbers.str(d.get("responsibleNo")),
                    BizNumbers.str(d.get("remark")), now);
        }

        String waveNo = BizNumbers.str(r.get("waveNo"));
        finishWaveIfDone(waveNo, now, me);

        operationLogService.log("REVIEW", reviewNo, "REVIEW_SUBMIT", "提交复核", reviewerNo, reviewerName, me.role(),
                "结果=" + Dict.desc("reviewStatus", status) + (diffQty != 0 ? "，差异=" + diffQty + " 件" : ""));
        return voAssembler.reviewVo(reviewNo);
    }

    public PageResult<Map<String, Object>> page(
            String reviewNo,
            String waveNo,
            String orderNo,
            String status,
            String diffType,
            String reviewerNo,
            int pageNum,
            int pageSize) {

        LoginUser me = AuthContext.require();
        // 复核员只看自己名下的复核单；主管/波次员可按工号筛选或看全量
        String scope = me.hasRole("ADMIN", "WAVE_OPERATOR")
                ? (notBlank(reviewerNo) ? reviewerNo.trim() : null)
                : me.employeeNo();

        StringBuilder q = new StringBuilder("SELECT * FROM review WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (notBlank(reviewNo)) { q.append(" AND review_no LIKE ?"); args.add("%" + reviewNo.trim() + "%"); }
        if (notBlank(waveNo)) { q.append(" AND wave_no=?"); args.add(waveNo); }
        if (notBlank(orderNo)) { q.append(" AND order_no=?"); args.add(orderNo); }
        if (notBlank(status)) { q.append(" AND status=?"); args.add(status); }
        if (notBlank(diffType)) { q.append(" AND diff_type=?"); args.add(diffType); }
        if (scope != null) { q.append(" AND reviewer_no=?"); args.add(scope); }
        q.append(" ORDER BY create_time DESC, id DESC");

        Map<String, Object> pg = this.sql.page(q.toString(), pageNum, pageSize, args.toArray());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) pg.get("records");
        for (Map<String, Object> r : rows) {
            voAssembler.enrichReview(r);
            r.put("shouldItems", sql.list(
                    "SELECT * FROM order_item WHERE order_no=? ORDER BY line_no", BizNumbers.str(r.get("orderNo"))));
            r.put("diffItems", sql.list(
                    "SELECT * FROM review_diff WHERE review_no=? ORDER BY id", BizNumbers.str(r.get("reviewNo"))));
        }
        return new PageResult<>(rows, ((Number) pg.get("total")).longValue(), pageNum, pageSize);
    }

    public Map<String, Object> detail(String reviewNo) {
        Map<String, Object> r = voAssembler.reviewVo(reviewNo);
        if (r == null) throw new IllegalArgumentException("复核单不存在：" + reviewNo);
        return r;
    }

    // ---------------- 内部 ----------------

    private void finishWaveIfDone(String waveNo, String now, LoginUser me) {
        if (waveNo == null) return;
        long waiting = sql.count(
                "SELECT COUNT(*) FROM review WHERE wave_no=? AND status='WAITING'", waveNo);
        if (waiting == 0) {
            sql.update("UPDATE wave SET status='REVIEWED', review_finish_time=? WHERE wave_no=?", now, waveNo);
            sql.update("UPDATE `order` SET status='REVIEWED' WHERE wave_no=? AND status='PICKED'", waveNo);
            operationLogService.log("REVIEW", waveNo, "REVIEW_FINISH", "复核完成", me.employeeNo(), me.employeeName(), me.role(),
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


