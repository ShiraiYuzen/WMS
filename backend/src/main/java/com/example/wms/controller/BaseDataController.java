package com.example.wms.controller;

import com.example.wms.common.Dict;
import com.example.wms.common.JdbcMaps;
import com.example.wms.common.RequireRole;
import com.example.wms.dto.ApiResult;
import com.example.wms.dto.PageResult;
import com.example.wms.service.Repo;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/base")
public class BaseDataController {

    private final Repo repo;

    public BaseDataController(Repo repo) {
        this.repo = repo;
    }

    @GetMapping("/enums")
    public ApiResult<Map<String, List<Map<String, String>>>> enums() {
        return ApiResult.ok(Dict.all());
    }

    // ---------------- SKU ----------------

    @GetMapping("/sku/categories")
    public ApiResult<List<String>> skuCategories() {
        List<String> r = new ArrayList<>();
        for (Map<String, Object> row : JdbcMaps.list(repo.jdbc(),
                "SELECT DISTINCT category FROM sku WHERE category IS NOT NULL ORDER BY category")) {
            r.add(Repo.str(row.get("category")));
        }
        return ApiResult.ok(r);
    }

    @GetMapping("/sku/page")
    public ApiResult<PageResult<Map<String, Object>>> skuPage(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        StringBuilder sql = new StringBuilder("SELECT * FROM sku WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (notBlank(keyword)) {
            sql.append(" AND (sku_code LIKE ? OR sku_name LIKE ? OR barcode LIKE ?)");
            String k = "%" + keyword.trim() + "%";
            args.add(k); args.add(k); args.add(k);
        }
        if (notBlank(category)) { sql.append(" AND category=?"); args.add(category); }
        sql.append(" ORDER BY sku_code ASC");
        Map<String, Object> pg = JdbcMaps.page(repo.jdbc(), sql.toString(), pageNum, pageSize, args.toArray());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) pg.get("records");
        return ApiResult.ok(new PageResult<>(rows, ((Number) pg.get("total")).longValue(), pageNum, pageSize));
    }

    // ---------------- 库位 ----------------

    @GetMapping("/location/zones")
    public ApiResult<List<String>> zones() {
        List<String> r = new ArrayList<>();
        for (Map<String, Object> row : JdbcMaps.list(repo.jdbc(),
                "SELECT DISTINCT zone FROM location WHERE zone IS NOT NULL ORDER BY zone")) {
            r.add(Repo.str(row.get("zone")));
        }
        return ApiResult.ok(r);
    }

    @GetMapping("/location/page")
    public ApiResult<PageResult<Map<String, Object>>> locationPage(
            @RequestParam(required = false) String zone,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        StringBuilder sql = new StringBuilder("SELECT * FROM location WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (notBlank(zone)) { sql.append(" AND zone=?"); args.add(zone); }
        sql.append(" ORDER BY path_seq ASC, location_code ASC");
        Map<String, Object> pg = JdbcMaps.page(repo.jdbc(), sql.toString(), pageNum, pageSize, args.toArray());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) pg.get("records");
        for (Map<String, Object> loc : rows) {
            loc.put("statusDesc", Dict.desc("locationStatus", Repo.str(loc.get("status"))));
        }
        return ApiResult.ok(new PageResult<>(rows, ((Number) pg.get("total")).longValue(), pageNum, pageSize));
    }

    // ---------------- 员工 ----------------

    @GetMapping("/employee/by-role")
    public ApiResult<List<Map<String, Object>>> employeeByRole(@RequestParam(required = false) String role) {
        List<Map<String, Object>> rows;
        if (notBlank(role)) {
            rows = JdbcMaps.list(repo.jdbc(),
                    "SELECT * FROM employee WHERE status=1 AND role=? ORDER BY employee_no", role);
        } else {
            rows = JdbcMaps.list(repo.jdbc(),
                    "SELECT * FROM employee WHERE status=1 ORDER BY employee_no");
        }
        for (Map<String, Object> e : rows) {
            e.put("roleDesc", Dict.desc("employeeRole", Repo.str(e.get("role"))));
        }
        return ApiResult.ok(rows);
    }

    @RequireRole({"ADMIN"})
    @GetMapping("/employee/teams")
    public ApiResult<List<String>> teams() {
        List<String> r = new ArrayList<>();
        for (Map<String, Object> row : JdbcMaps.list(repo.jdbc(),
                "SELECT DISTINCT team FROM employee WHERE team IS NOT NULL ORDER BY team")) {
            r.add(Repo.str(row.get("team")));
        }
        return ApiResult.ok(r);
    }

    /** 全量人员档案（含手机号等 PII）：仅仓库主管可查 */
    @RequireRole({"ADMIN"})
    @GetMapping("/employee/page")
    public ApiResult<PageResult<Map<String, Object>>> employeePage(
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String team,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        StringBuilder sql = new StringBuilder("SELECT * FROM employee WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (notBlank(role)) { sql.append(" AND role=?"); args.add(role); }
        if (notBlank(team)) { sql.append(" AND team=?"); args.add(team); }
        sql.append(" ORDER BY employee_no ASC");
        Map<String, Object> pg = JdbcMaps.page(repo.jdbc(), sql.toString(), pageNum, pageSize, args.toArray());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) pg.get("records");
        for (Map<String, Object> e : rows) {
            e.put("roleDesc", Dict.desc("employeeRole", Repo.str(e.get("role"))));
        }
        return ApiResult.ok(new PageResult<>(rows, ((Number) pg.get("total")).longValue(), pageNum, pageSize));
    }

    // ---------------- 承运商 ----------------

    @GetMapping("/carrier/list")
    public ApiResult<List<Map<String, Object>>> carrierList() {
        return ApiResult.ok(JdbcMaps.list(repo.jdbc(),
                "SELECT * FROM carrier WHERE status=1 ORDER BY carrier_code"));
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
