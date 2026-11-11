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

import java.util.*;

@Service
public class HandoverService {

    private final SqlSupport sql;
    private final BizNumbers bizNumbers;
    private final VoAssembler voAssembler;
    private final OperationLogService operationLogService;

    public HandoverService(SqlSupport sql, BizNumbers bizNumbers, VoAssembler voAssembler, OperationLogService operationLogService) {
        this.sql = sql;
        this.bizNumbers = bizNumbers;
        this.voAssembler = voAssembler;
        this.operationLogService = operationLogService;
    }
    /** 可交接波次：交接岗的工作队列，只对复核员与主管开放 */
    public List<Map<String, Object>> reviewableWaves(String carrierCode) {
        StringBuilder q = new StringBuilder(
                "SELECT DISTINCT w.* FROM wave w JOIN wave_order wo ON wo.wave_no=w.wave_no " +
                        "JOIN `order` o ON o.order_no=wo.order_no WHERE w.status='REVIEWED'");
        List<Object> args = new ArrayList<>();
        if (carrierCode != null && !carrierCode.isBlank()) {
            q.append(" AND o.carrier_code=?");
            args.add(carrierCode);
        }
        q.append(" ORDER BY w.review_finish_time ASC, w.id ASC");
        List<Map<String, Object>> waves = this.sql.list(q.toString(), args.toArray());
        for (Map<String, Object> w : waves) voAssembler.enrichWave(w);
        return waves;
    }

    /** 创建交接单：交接人恒为当前登录人（SEC-18） */
    public Map<String, Object> create(Map<String, Object> body) {
        LoginUser me = AuthContext.require();
        String carrierCode = BizNumbers.str(body.get("carrierCode"));
        if (carrierCode == null || carrierCode.isBlank()) throw new IllegalArgumentException("请选择承运商");
        @SuppressWarnings("unchecked")
        List<String> waveNos = body.get("waveNos") instanceof List<?> l
                ? new ArrayList<>((List<String>) l) : Collections.emptyList();
        if (waveNos.isEmpty()) throw new IllegalArgumentException("请选择要交接的波次");

        String carrierName = voAssembler.carrierName(carrierCode);
        int orderCount = 0;
        int packageCount = 0;
        double totalWeight = 0d;
        for (String waveNo : waveNos) {
            Map<String, Object> w = sql.one( "SELECT * FROM wave WHERE wave_no=?", waveNo);
            if (w == null) throw new IllegalArgumentException("波次不存在：" + waveNo);
            String st = BizNumbers.str(w.get("status"));
            if (!"REVIEWED".equals(st)) {
                throw new IllegalStateException("波次 " + waveNo + " 当前状态（"
                        + Dict.desc("waveStatus", st) + "）不可交接");
            }
            orderCount += BizNumbers.i(w.get("orderCount"));
            Map<String, Object> agg = sql.one(
                    "SELECT COALESCE(SUM(package_count),0) pc, COALESCE(SUM(weight),0) wt FROM review WHERE wave_no=?", waveNo);
            if (agg != null) {
                packageCount += BizNumbers.i(agg.get("pc"));
                totalWeight += BizNumbers.d(agg.get("wt"));
            }
        }

        String handoverNo = bizNumbers.nextHandoverNo();
        String now = BizNumbers.now();
        sql.update("INSERT INTO handover (handover_no,wave_nos,carrier_code,carrier_name,order_count," +
                        "package_count,total_weight,status,handover_user_no,handover_user_name,vehicle_no,create_time,remark) " +
                        "VALUES (?,?,?,?,?,?,?, 'CREATED', ?,?,?,?,?)",
                handoverNo, String.join(",", waveNos), carrierCode, carrierName, orderCount,
                packageCount, totalWeight,
                me.employeeNo(), me.employeeName(),
                BizNumbers.str(body.get("vehicleNo")), now, BizNumbers.str(body.get("remark")));
        operationLogService.log("HANDOVER", handoverNo, "HANDOVER_CREATE", "创建交接单",
                me.employeeNo(), me.employeeName(), me.role(),
                "承运商=" + carrierName + "，波次=" + String.join(",", waveNos));
        return voAssembler.handoverVo(handoverNo);
    }

    /**
     * 确认/拒收交接单。
     *
     * <p>只允许 CREATED → CONFIRMED / REJECTED，已确认的单据不能再次确认，
     * 否则可用后一次请求覆盖包数与签收人（SEC-16）。</p>
     */
    public Map<String, Object> confirm(Map<String, Object> body) {
        LoginUser me = AuthContext.require();
        String handoverNo = BizNumbers.str(body.get("handoverNo"));
        Map<String, Object> h = sql.one( "SELECT * FROM handover WHERE handover_no=?", handoverNo);
        if (h == null) throw new IllegalArgumentException("交接单不存在：" + handoverNo);

        String cur = BizNumbers.str(h.get("status"));
        if (!"CREATED".equals(cur)) {
            throw new IllegalStateException("该交接单已是「" + Dict.desc("handoverStatus", cur)
                    + "」，不能重复确认");
        }

        boolean rejected = body.get("rejected") != null && Boolean.parseBoolean(BizNumbers.str(body.get("rejected")));
        String now = BizNumbers.now();
        String status = rejected ? "REJECTED" : "CONFIRMED";
        Integer packageCount = body.get("actualPackageCount") == null ? null : BizNumbers.i(body.get("actualPackageCount"));
        if (packageCount != null && packageCount < 0) {
            throw new IllegalArgumentException("实际包数不能为负数");
        }

        int n = sql.update("UPDATE handover SET status=?, receive_name=?, package_count=COALESCE(?,package_count), " +
                        "handover_time=?, remark=COALESCE(?,remark) WHERE handover_no=? AND status='CREATED'",
                status, BizNumbers.str(body.get("receiveName")), packageCount, now,
                BizNumbers.str(body.get("remark")), handoverNo);
        if (n == 0) {
            throw new IllegalStateException("该交接单已被其他人确认，请刷新后查看");
        }

        if (!rejected) {
            List<String> waveNos = splitWaves(BizNumbers.str(h.get("waveNos")));
            for (String waveNo : waveNos) {
                sql.update("UPDATE wave SET status='HANDOVERED', handover_time=? " +
                        "WHERE wave_no=? AND status='REVIEWED'", now, waveNo);
                sql.update("UPDATE `order` SET status='OUTBOUND' WHERE wave_no=? AND status='REVIEWED'", waveNo);
            }
        }
        operationLogService.log("HANDOVER", handoverNo, "HANDOVER_CONFIRM", rejected ? "承运商拒收" : "交接完成",
                me.employeeNo(), me.employeeName(), me.role(),
                "状态=" + Dict.desc("handoverStatus", status));
        return voAssembler.handoverVo(handoverNo);
    }

    public PageResult<Map<String, Object>> page(
            String handoverNo,
            String carrierCode,
            String status,
            int pageNum,
            int pageSize) {

        StringBuilder q = new StringBuilder("SELECT * FROM handover WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (notBlank(handoverNo)) { q.append(" AND handover_no LIKE ?"); args.add("%" + handoverNo.trim() + "%"); }
        if (notBlank(carrierCode)) { q.append(" AND carrier_code=?"); args.add(carrierCode); }
        if (notBlank(status)) { q.append(" AND status=?"); args.add(status); }
        q.append(" ORDER BY create_time DESC, id DESC");

        Map<String, Object> pg = this.sql.page(q.toString(), pageNum, pageSize, args.toArray());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) pg.get("records");
        for (Map<String, Object> h : rows) {
            h.put("statusDesc", Dict.desc("handoverStatus", BizNumbers.str(h.get("status"))));
        }
        return new PageResult<>(rows, ((Number) pg.get("total")).longValue(), pageNum, pageSize);
    }

    private static List<String> splitWaves(String waveNos) {
        if (waveNos == null || waveNos.isBlank()) return Collections.emptyList();
        return Arrays.asList(waveNos.split(",");
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}


