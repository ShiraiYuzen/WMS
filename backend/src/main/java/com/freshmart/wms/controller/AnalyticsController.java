package com.freshmart.wms.controller;

import com.freshmart.wms.common.R;
import com.freshmart.wms.dto.AnalyticsQuery;
import com.freshmart.wms.service.AnalyticsService;
import com.freshmart.wms.vo.EmployeeEfficiencyVO;
import com.freshmart.wms.vo.ErrorRateVO;
import com.freshmart.wms.vo.OverviewVO;
import com.freshmart.wms.vo.TrendPointVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 效率复盘接口 —— 闭环第六环「效率复盘」。
 */
@Tag(name = "07. 效率复盘", description = "总览 KPI、人员效率、差错率、趋势、缺货分析")
@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    @Operation(summary = "总览 KPI", description = "含拣货效率、差错率、缺货率、复核通过率及超阈值预警")
    @GetMapping("/overview")
    public R<OverviewVO> overview(AnalyticsQuery query) {
        return R.ok(analyticsService.overview(query));
    }

    @Operation(summary = "人员效率排行", description = "拣货员看效率，复核员看准确度，综合评分排序")
    @GetMapping("/employee-efficiency")
    public R<List<EmployeeEfficiencyVO>> employeeEfficiency(AnalyticsQuery query) {
        return R.ok(analyticsService.employeeEfficiency(query));
    }

    @Operation(summary = "差错率统计", description = "按差异类型、责任环节分布，并给出差错率最高的人员")
    @GetMapping("/error-rate")
    public R<ErrorRateVO> errorRate(AnalyticsQuery query) {
        return R.ok(analyticsService.errorRate(query));
    }

    @Operation(summary = "作业趋势", description = "按天/周聚合波次数、订单数、拣货件数、效率、差错率")
    @GetMapping("/trend")
    public R<List<TrendPointVO>> trend(AnalyticsQuery query) {
        return R.ok(analyticsService.trend(query));
    }

    @Operation(summary = "缺货原因分布")
    @GetMapping("/shortage-distribution")
    public R<List<ErrorRateVO.TypeDistribution>> shortageDistribution(AnalyticsQuery query) {
        return R.ok(analyticsService.shortageDistribution(query));
    }
}
