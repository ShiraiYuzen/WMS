package com.example.wms.controller;

import com.example.wms.common.AuthContext;
import com.example.wms.common.JdbcMaps;
import com.example.wms.common.LoginUser;
import com.example.wms.common.RequireRole;
import com.example.wms.dto.ApiResult;
import com.example.wms.dto.PageResult;
import com.example.wms.service.Repo;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/order")
public class OrderController {

    private final Repo repo;

    public OrderController(Repo repo) {
        this.repo = repo;
    }

    @GetMapping("/count-by-status")
    public ApiResult<Map<String, Object>> countByStatus() {
        Map<String, Object> r = new LinkedHashMap<>();
        for (String code : new String[]{"CREATED", "POOLED", "WAVED", "PICKED", "REVIEWED", "OUTBOUND", "CANCELLED"}) {
            r.put(code, 0);
        }
        for (Map<String, Object> row : JdbcMaps.list(repo.jdbc(),
                "SELECT status, COUNT(*) c FROM `order` GROUP BY status")) {
            r.put(Repo.str(row.get("status")), row.get("c"));
        }
        return ApiResult.ok(r);
    }

    @GetMapping("/page")
    public ApiResult<PageResult<Map<String, Object>>> page(
            @RequestParam(required = false) String orderNo,
            @RequestParam(required = false) String platform,
            @RequestParam(required = false) String carrierCode,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String province,
            @RequestParam(required = false) String priority,
            @RequestParam(required = false) String waveNo,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {

        StringBuilder sql = new StringBuilder("SELECT * FROM `order` WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (notBlank(orderNo)) {
            sql.append(" AND (order_no LIKE ? OR receiver_name LIKE ?)");
            String k = "%" + orderNo.trim() + "%";
            args.add(k); args.add(k);
        }
        if (notBlank(platform)) { sql.append(" AND platform=?"); args.add(platform); }
        if (notBlank(carrierCode)) { sql.append(" AND carrier_code=?"); args.add(carrierCode); }
        if (notBlank(status)) { sql.append(" AND status=?"); args.add(status); }
        if (notBlank(province)) { sql.append(" AND province=?"); args.add(province); }
        if (notBlank(priority)) { sql.append(" AND priority=?"); args.add(priority); }
        if (notBlank(waveNo)) { sql.append(" AND wave_no=?"); args.add(waveNo); }
        if (notBlank(startDate)) { sql.append(" AND LEFT(pay_time,10) >= ?"); args.add(startDate); }
        if (notBlank(endDate)) { sql.append(" AND LEFT(pay_time,10) <= ?"); args.add(endDate); }
        sql.append(" ORDER BY CASE priority WHEN 'URGENT' THEN 0 ELSE 1 END, pay_time DESC, id DESC");

        Map<String, Object> pg = JdbcMaps.page(repo.jdbc(), sql.toString(), pageNum, pageSize, args.toArray());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) pg.get("records");
        for (Map<String, Object> o : rows) {
            repo.enrichOrder(o);
            o.put("items", JdbcMaps.list(repo.jdbc(),
                    "SELECT * FROM order_item WHERE order_no=? ORDER BY line_no", Repo.str(o.get("orderNo"))));
        }
        return ApiResult.ok(new PageResult<>(rows, ((Number) pg.get("total")).longValue(), pageNum, pageSize));
    }

    @RequireRole({"ADMIN", "WAVE_OPERATOR"})
    @PostMapping("/pool")
    public ApiResult<Integer> pool(@RequestBody Map<String, Object> body) {
        LoginUser me = AuthContext.require();
        Object raw = body == null ? null : body.get("orderNos");
        if (!(raw instanceof List<?> list) || list.isEmpty()) {
            throw new IllegalArgumentException("请选择要汇聚的订单");
        }
        String placeholders = String.join(",", Collections.nCopies(list.size(), "?"));
        List<Object> args = new ArrayList<>(list);
        int n = repo.jdbc().update(
                "UPDATE `order` SET status='POOLED' WHERE status IN ('CREATED','POOLED') AND order_no IN (" + placeholders + ")",
                args.toArray());
        if (n == 0) throw new IllegalStateException("所选订单中无可汇聚的订单");
        repo.log("ORDER", null, "ORDER_POOL", "订单汇聚", me.employeeNo(), me.employeeName(), me.role(),
                "汇聚订单 " + n + " 单");
        return ApiResult.ok(n);
    }

    @RequireRole({"ADMIN", "WAVE_OPERATOR"})
    @PostMapping("/pool-all")
    public ApiResult<Integer> poolAll() {
        LoginUser me = AuthContext.require();
        int n = repo.jdbc().update("UPDATE `order` SET status='POOLED' WHERE status='CREATED'");
        repo.log("ORDER", null, "ORDER_POOL_ALL", "一键汇聚全部订单", me.employeeNo(), me.employeeName(), me.role(),
                "汇聚订单 " + n + " 单");
        return ApiResult.ok(n);
    }

    @RequireRole({"ADMIN", "WAVE_OPERATOR"})
    @PostMapping("/{orderNo}/cancel")
    public ApiResult<Map<String, Object>> cancel(@PathVariable String orderNo) {
        LoginUser me = AuthContext.require();
        Map<String, Object> o = JdbcMaps.one(repo.jdbc(), "SELECT * FROM `order` WHERE order_no=?", orderNo);
        if (o == null) throw new IllegalArgumentException("订单不存在：" + orderNo);
        String st = Repo.str(o.get("status"));
        if (!"CREATED".equals(st) && !"POOLED".equals(st)) {
            throw new IllegalStateException("订单已进入波次，不能取消");
        }
        repo.jdbc().update("UPDATE `order` SET status='CANCELLED' WHERE order_no=?", orderNo);
        repo.log("ORDER", orderNo, "ORDER_CANCEL", "取消订单", me.employeeNo(), me.employeeName(), me.role(),
                "原状态=" + st);
        return ApiResult.ok(repo.orderVo(orderNo));
    }

    @GetMapping("/{orderNo}")
    public ApiResult<Map<String, Object>> detail(@PathVariable String orderNo) {
        Map<String, Object> o = repo.orderVo(orderNo);
        if (o == null) throw new IllegalArgumentException("订单不存在：" + orderNo);
        return ApiResult.ok(o);
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
