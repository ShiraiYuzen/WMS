package com.freshmart.wms.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDate;

/**
 * 效率复盘查询条件。
 */
@Data
@Accessors(chain = true)
@Schema(description = "效率复盘查询条件")
public class AnalyticsQuery {

    @Schema(description = "统计开始日期，默认近 7 天")
    private LocalDate startDate;

    @Schema(description = "统计结束日期，默认今天")
    private LocalDate endDate;

    @Schema(description = "按人员筛选（工号）")
    private String employeeNo;

    @Schema(description = "趋势粒度：DAY / WEEK", example = "DAY")
    private String granularity = "DAY";

    @Schema(description = "排行榜取前 N 名", example = "10")
    private Integer topN = 10;
}
