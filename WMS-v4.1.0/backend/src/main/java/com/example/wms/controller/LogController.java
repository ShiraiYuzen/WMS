package com.example.wms.controller;

import com.example.wms.common.JdbcMaps;
import com.example.wms.common.RequireRole;
import com.example.wms.dto.ApiResult;
import com.example.wms.dto.PageResult;
import com.example.wms.service.Repo;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 操作日志：/page 含全员工号与业务明细，仅仓库主管可查；/trace 为单据时间线，登录即可看 */
@RestController
@RequestMapping("/api/log")
public class LogController {

    private final Repo repo;

    public LogController(Repo repo) {
        this.repo = repo;
    }

    @GetMapping("/trace")
    public ApiResult<List<Map<String, Object>>> trace(@RequestParam String bizNo) {
        return ApiResult.ok(JdbcMaps.list(repo.jdbc(),
                "SELECT * FROM operation_log WHERE biz_no=? ORDER BY operate_time DESC, id DESC", bizNo));
    }

    @RequireRole({"ADMIN"})
    @GetMapping("/page")
    public ApiResult<PageResult<Map<String, Object>>> page(
            @RequestParam(required = false) String bizType,
            @RequestParam(required = false) String bizNo,
            @RequestParam(required = false) String operatorNo,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {

        StringBuilder sql = new StringBuilder("SELECT * FROM operation_log WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (notBlank(bizType)) { sql.append(" AND biz_type=?"); args.add(bizType); }
        if (notBlank(bizNo)) { sql.append(" AND biz_no LIKE ?"); args.add("%" + bizNo.trim() + "%"); }
        if (notBlank(operatorNo)) { sql.append(" AND operator_no=?"); args.add(operatorNo); }
        sql.append(" ORDER BY operate_time DESC, id DESC");

        Map<String, Object> pg = JdbcMaps.page(repo.jdbc(), sql.toString(), pageNum, pageSize, args.toArray());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) pg.get("records");
        return ApiResult.ok(new PageResult<>(rows, ((Number) pg.get("total")).longValue(), pageNum, pageSize));
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
