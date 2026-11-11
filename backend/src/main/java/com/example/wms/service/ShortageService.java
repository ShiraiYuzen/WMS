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
public class ShortageService {

    private final SqlSupport sql;
    private final BizNumbers bizNumbers;
    private final VoAssembler voAssembler;
    private final OperationLogService operationLogService;

    public ShortageService(SqlSupport sql, BizNumbers bizNumbers, VoAssembler voAssembler, OperationLogService operationLogService) {
        this.sql = sql;
        this.bizNumbers = bizNumbers;
        this.voAssembler = voAssembler;
        this.operationLogService = operationLogService;
    }
    public Long pendingCount() {
        return this.sql.count("SELECT COUNT(*) FROM shortage WHERE status='PENDING'");
    }

    public Map<String, Object> reasonDistribution() {
        Map<String, Object> r = new LinkedHashMap<>();
        for (Map<String, Object> row : sql.list(
                "SELECT reason, COUNT(*) c FROM shortage GROUP BY reason")) {
            r.put(Dict.desc("shortageReason", BizNumbers.str(row.get("reason"))), row.get("c"));
        }
        return r;
    }

    public Map<String, Object> handle(Map<String, Object> body) {
        LoginUser me = AuthContext.require();
        long id = longVal(body.get("id"));
        Map<String, Object> s = sql.one( "SELECT * FROM shortage WHERE id=?", id);
        if (s == null) throw new IllegalArgumentException("缺货记录不存在");
        String handleType = BizNumbers.str(body.get("handleType"));
        if (handleType == null || handleType.isBlank()) throw new IllegalArgumentException("请选择处理方式");
        sql.update("UPDATE shortage SET status='HANDLED', handle_type=?, handler_no=?, handler_name=?, " +
                        "handle_time=?, handle_remark=?, substitute_sku_code=? WHERE id=?",
                handleType, me.employeeNo(), me.employeeName(),
                BizNumbers.now(), BizNumbers.str(body.get("handleRemark")), BizNumbers.str(body.get("substituteSkuCode")), id);
        operationLogService.log("SHORTAGE", BizNumbers.str(s.get("shortageNo")), "SHORTAGE_HANDLE", "处理缺货",
                me.employeeNo(), me.employeeName(), me.role(),
                "处理方式=" + Dict.desc("shortageHandleType", handleType));
        return voAssembler.shortageVo(id);
    }

    public Map<String, Object> close(long id) {
        LoginUser me = AuthContext.require();
        Map<String, Object> s = sql.one( "SELECT * FROM shortage WHERE id=?", id);
        if (s == null) throw new IllegalArgumentException("缺货记录不存在");
        sql.update("UPDATE shortage SET status='CLOSED' WHERE id=?", id);
        operationLogService.log("SHORTAGE", BizNumbers.str(s.get("shortageNo")), "SHORTAGE_CLOSE", "归档缺货",
                me.employeeNo(), me.employeeName(), me.role(), null);
        return voAssembler.shortageVo(id);
    }

    public PageResult<Map<String, Object>> page(
            String shortageNo,
            String waveNo,
            String skuCode,
            String status,
            String reason,
            int pageNum,
            int pageSize) {

        StringBuilder q = new StringBuilder("SELECT * FROM shortage WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (notBlank(shortageNo)) { q.append(" AND shortage_no LIKE ?"); args.add("%" + shortageNo.trim() + "%"); }
        if (notBlank(waveNo)) { q.append(" AND wave_no=?"); args.add(waveNo); }
        if (notBlank(skuCode)) { q.append(" AND sku_code=?"); args.add(skuCode); }
        if (notBlank(status)) { q.append(" AND status=?"); args.add(status); }
        if (notBlank(reason)) { q.append(" AND reason=?"); args.add(reason); }
        q.append(" ORDER BY report_time DESC, id DESC");

        Map<String, Object> pg = this.sql.page(q.toString(), pageNum, pageSize, args.toArray());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) pg.get("records");
        for (Map<String, Object> s : rows) voAssembler.enrichShortage(s);
        return new PageResult<>(rows, ((Number) pg.get("total")).longValue(), pageNum, pageSize);
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


