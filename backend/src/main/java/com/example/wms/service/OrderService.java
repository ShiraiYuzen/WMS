package com.example.wms.service;

import com.example.wms.common.AuthContext;
import com.example.wms.common.SqlSupport;
import com.example.wms.common.LoginUser;
import com.example.wms.dto.PageResult;
import com.example.wms.service.support.BizNumbers;
import com.example.wms.service.support.OperationLogService;
import com.example.wms.service.support.VoAssembler;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class OrderService {

    private final SqlSupport sql;
    private final VoAssembler voAssembler;
    private final OperationLogService operationLogService;

    public OrderService(SqlSupport sql, VoAssembler voAssembler, OperationLogService operationLogService) {
        this.sql = sql;
        this.voAssembler = voAssembler;
        this.operationLogService = operationLogService;
    }
    public Map<String, Object> countByStatus() {
        Map<String, Object> r = new LinkedHashMap<>();
        for (String code : new String[]{"CREATED", "POOLED", "WAVED", "PICKED", "REVIEWED", "OUTBOUND", "CANCELLED"}) {
            r.put(code, 0);
        }
        for (Map<String, Object> row : sql.list(
                "SELECT status, COUNT(*) c FROM `order` GROUP BY status")) {
            r.put(BizNumbers.str(row.get("status")), row.get("c"));
        }
        return r;
    }

    public PageResult<Map<String, Object>> page(
            String orderNo,
            String platform,
            String carrierCode,
            String status,
            String province,
            String priority,
            String waveNo,
            String startDate,
            String endDate,
            int pageNum,
            int pageSize) {

        StringBuilder q = new StringBuilder("SELECT * FROM `order` WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (notBlank(orderNo)) {
            q.append(" AND (order_no LIKE ? OR receiver_name LIKE ?)");
            String k = "%" + orderNo.trim() + "%";
            args.add(k); args.add(k);
        }
        if (notBlank(platform)) { q.append(" AND platform=?"); args.add(platform); }
        if (notBlank(carrierCode)) { q.append(" AND carrier_code=?"); args.add(carrierCode); }
        if (notBlank(status)) { q.append(" AND status=?"); args.add(status); }
        if (notBlank(province)) { q.append(" AND province=?"); args.add(province); }
        if (notBlank(priority)) { q.append(" AND priority=?"); args.add(priority); }
        if (notBlank(waveNo)) { q.append(" AND wave_no=?"); args.add(waveNo); }
        if (notBlank(startDate)) { q.append(" AND LEFT(pay_time,10) >= ?"); args.add(startDate); }
        if (notBlank(endDate)) { q.append(" AND LEFT(pay_time,10) <= ?"); args.add(endDate); }
        q.append(" ORDER BY CASE priority WHEN 'URGENT' THEN 0 ELSE 1 END, pay_time DESC, id DESC");

        Map<String, Object> pg = this.sql.page(q.toString(), pageNum, pageSize, args.toArray());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) pg.get("records");
        for (Map<String, Object> o : rows) {
            voAssembler.enrichOrder(o);
            o.put("items", sql.list(
                    "SELECT * FROM order_item WHERE order_no=? ORDER BY line_no", BizNumbers.str(o.get("orderNo"))));
        }
        return new PageResult<>(rows, ((Number) pg.get("total")).longValue(), pageNum, pageSize);
    }

    public Integer pool(Map<String, Object> body) {
        LoginUser me = AuthContext.require();
        Object raw = body == null ? null : body.get("orderNos");
        if (!(raw instanceof List<?> list) || list.isEmpty()) {
            throw new IllegalArgumentException("请选择要汇聚的订单");
        }
        String placeholders = String.join(",", Collections.nCopies(list.size(), "?"));
        List<Object> args = new ArrayList<>(list);
        int n = sql.update(
                "UPDATE `order` SET status='POOLED' WHERE status IN ('CREATED','POOLED') AND order_no IN (" + placeholders + ")",
                args.toArray());
        if (n == 0) throw new IllegalStateException("所选订单中无可汇聚的订单");
        operationLogService.log("ORDER", null, "ORDER_POOL", "订单汇聚", me.employeeNo(), me.employeeName(), me.role(),
                "汇聚订单 " + n + " 单");
        return n;
    }

    public Integer poolAll() {
        LoginUser me = AuthContext.require();
        int n = sql.update("UPDATE `order` SET status='POOLED' WHERE status='CREATED'");
        operationLogService.log("ORDER", null, "ORDER_POOL_ALL", "一键汇聚全部订单", me.employeeNo(), me.employeeName(), me.role(),
                "汇聚订单 " + n + " 单");
        return n;
    }

    public Map<String, Object> cancel(String orderNo) {
        LoginUser me = AuthContext.require();
        Map<String, Object> o = sql.one( "SELECT * FROM `order` WHERE order_no=?", orderNo);
        if (o == null) throw new IllegalArgumentException("订单不存在：" + orderNo);
        String st = BizNumbers.str(o.get("status"));
        if (!"CREATED".equals(st) && !"POOLED".equals(st)) {
            throw new IllegalStateException("订单已进入波次，不能取消");
        }
        sql.update("UPDATE `order` SET status='CANCELLED' WHERE order_no=?", orderNo);
        operationLogService.log("ORDER", orderNo, "ORDER_CANCEL", "取消订单", me.employeeNo(), me.employeeName(), me.role(),
                "原状态=" + st);
        return voAssembler.orderVo(orderNo);
    }

    public Map<String, Object> detail(String orderNo) {
        Map<String, Object> o = voAssembler.orderVo(orderNo);
        if (o == null) throw new IllegalArgumentException("订单不存在：" + orderNo);
        return o;
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}


