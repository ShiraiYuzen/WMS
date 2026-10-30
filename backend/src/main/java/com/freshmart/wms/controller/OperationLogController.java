package com.freshmart.wms.controller;

import com.freshmart.wms.common.PageQuery;
import com.freshmart.wms.common.PageResult;
import com.freshmart.wms.common.R;
import com.freshmart.wms.domain.entity.OperationLog;
import com.freshmart.wms.repository.OperationLogRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 作业追溯接口。记录每一次状态流转，用于事后追溯与复盘佐证。
 */
@Tag(name = "09. 作业追溯", description = "操作日志查询")
@RestController
@RequestMapping("/api/log")
@RequiredArgsConstructor
public class OperationLogController {

    private final OperationLogRepository logRepo;

    @Operation(summary = "操作日志分页查询")
    @GetMapping("/page")
    public R<PageResult<OperationLog>> page(@RequestParam(required = false) String bizType,
                                            @RequestParam(required = false) String bizNo,
                                            @RequestParam(required = false) String operatorNo,
                                            PageQuery query) {
        List<OperationLog> all = logRepo.findAll().stream()
                .filter(l -> bizType == null || bizType.isBlank() || bizType.equals(l.getBizType()))
                .filter(l -> bizNo == null || bizNo.isBlank() || bizNo.equals(l.getBizNo()))
                .filter(l -> operatorNo == null || operatorNo.isBlank() || operatorNo.equals(l.getOperatorNo()))
                .sorted(Comparator.comparing(OperationLog::getOperateTime,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .collect(Collectors.toList());
        return R.ok(PageResult.of(all, query));
    }

    @Operation(summary = "某单据的完整流转轨迹")
    @GetMapping("/trace")
    public R<List<OperationLog>> trace(@RequestParam String bizNo) {
        return R.ok(logRepo.find(l -> bizNo.equals(l.getBizNo())).stream()
                .sorted(Comparator.comparing(OperationLog::getOperateTime,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .collect(Collectors.toList()));
    }
}
