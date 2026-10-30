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

import java.util.*;

@RestController
@RequestMapping("/api/shortage")
public class ShortageController {

    private final Repo repo;

    public ShortageController(Repo repo) {
        this.repo = repo;
    }

    @GetMapping("/pending-count")
    public ApiResult<Long> pendingCount() {
        return ApiResult.ok(JdbcMaps.count(repo.jdbc(), "SELECT COUNT(*) FROM shortage WHERE status='PENDING'"));
    }

    @GetMapping("/reason-distribution")
    public ApiResult<Map<String, Object>> reasonDistribution() {
        Map<String, Object> r = new LinkedHashMap<>();
        for (Map<String, Object> row : JdbcMaps.list(repo.jdbc(),
                "SELECT reason, COUNT(*) c FROM shortage GROUP BY reason")) {
            r.put(Dict.desc("shortageReason", Repo.str(row.get("reason"))), row.get("c"));
        }
        return ApiResult.ok(r);
    }

    @RequireRole({"ADMIN", "WAVE_OPERATOR"})
    @PostMapping("/handle")
    public ApiResult<Map<String, Object>> handle(@RequestBody Map<String, Object> body) {
        LoginUser me = AuthContext.require();
        long id = longVal(body.get("id"));
        Map<String, Object> s = JdbcMaps.one(repo.jdbc(), "SELECT * FROM shortage WHERE id=?", id);
        if (s == null) throw new IllegalArgumentException("缺货记录不存在");
        String handleType = Repo.str(body.get("handleType"));
        if (handleType == null || handleType.isBlank()) throw new IllegalArgumentException("请选择处理方式");
        repo.jdbc().update("UPDATE shortage SET status='HANDLED', handle_type=?, handler_no=?, handler_name=?, " +
                        "handle_time=?, handle_remark=?, substitute_sku_code=? WHERE id=?",
                handleType, me.employeeNo(), me.employeeName(),
                Repo.now(), Repo.str(body.get("handleRemark")), Repo.str(body.get("substituteSkuCode")), id);
        repo.log("SHORTAGE", Repo.str(s.get("shortageNo")), "SHORTAGE_HANDLE", "处理缺货",
                me.employeeNo(), me.employeeName(), me.role(),
                "处理方式=" + Dict.desc("shortageHandleType", handleType));
        return ApiResult.ok(repo.shortageVo(id));
    }

    @RequireRole({"ADMIN", "WAVE_OPERATOR"})
    @PostMapping("/{id}/close")
    public ApiResult<Map<String, Object>> close(@PathVariable long id) {
        LoginUser me = AuthContext.require();
        Map<String, Object> s = JdbcMaps.one(repo.jdbc(), "SELECT * FROM shortage WHERE id=?", id);
        if (s == null) throw new IllegalArgumentException("缺货记录不存在");
        repo.jdbc().update("UPDATE shortage SET status='CLOSED' WHERE id=?", id);
        repo.log("SHORTAGE", Repo.str(s.get("shortageNo")), "SHORTAGE_CLOSE", "归档缺货",
                me.employeeNo(), me.employeeName(), me.role(), null);
        return ApiResult.ok(repo.shortageVo(id));
    }

    @GetMapping("/page")
    public ApiResult<PageResult<Map<String, Object>>> page(
            @RequestParam(required = false) String shortageNo,
            @RequestParam(required = false) String waveNo,
            @RequestParam(required = false) String skuCode,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String reason,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {

        StringBuilder sql = new StringBuilder("SELECT * FROM shortage WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (notBlank(shortageNo)) { sql.append(" AND shortage_no LIKE ?"); args.add("%" + shortageNo.trim() + "%"); }
        if (notBlank(waveNo)) { sql.append(" AND wave_no=?"); args.add(waveNo); }
        if (notBlank(skuCode)) { sql.append(" AND sku_code=?"); args.add(skuCode); }
        if (notBlank(status)) { sql.append(" AND status=?"); args.add(status); }
        if (notBlank(reason)) { sql.append(" AND reason=?"); args.add(reason); }
        sql.append(" ORDER BY report_time DESC, id DESC");

        Map<String, Object> pg = JdbcMaps.page(repo.jdbc(), sql.toString(), pageNum, pageSize, args.toArray());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) pg.get("records");
        for (Map<String, Object> s : rows) repo.enrichShortage(s);
        return ApiResult.ok(new PageResult<>(rows, ((Number) pg.get("total")).longValue(), pageNum, pageSize));
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
