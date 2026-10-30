package com.freshmart.wms.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;

/**
 * 效率复盘 —— 总览 KPI。
 */
@Data
@Accessors(chain = true)
@Schema(description = "效率复盘总览")
public class OverviewVO {

    /* ---------------- 订单与波次 ---------------- */

    @Schema(description = "订单总数")
    private Long totalOrders = 0L;

    @Schema(description = "待汇聚订单数")
    private Long pendingOrders = 0L;

    @Schema(description = "已入池待波次订单数")
    private Long pooledOrders = 0L;

    @Schema(description = "波次总数")
    private Long totalWaves = 0L;

    @Schema(description = "在制波次（拣货/复核中）")
    private Long workingWaves = 0L;

    @Schema(description = "已完成波次")
    private Long finishedWaves = 0L;

    /* ---------------- 作业量 ---------------- */

    @Schema(description = "已拣总件数")
    private Long pickedQty = 0L;

    @Schema(description = "已出库订单数")
    private Long outboundOrders = 0L;

    @Schema(description = "已交接包裹数")
    private Long handedPackages = 0L;

    /* ---------------- 核心指标 ---------------- */

    @Schema(description = "拣货效率（件/小时）")
    private Double pickEfficiency = 0.0;

    @Schema(description = "平均波次拣货时长（秒）")
    private Double avgPickDurationSec = 0.0;

    @Schema(description = "差错率（差异单数 / 复核单数）")
    private Double errorRate = 0.0;

    @Schema(description = "缺货率（缺货件数 / 应拣件数）")
    private Double shortageRate = 0.0;

    @Schema(description = "复核通过率")
    private Double reviewPassRate = 0.0;

    @Schema(description = "缺货待处理数")
    private Long pendingShortageCount = 0L;

    /* ---------------- 预警 ---------------- */

    @Schema(description = "指标预警列表")
    private List<MetricAlert> alerts = new ArrayList<>();

    /**
     * 指标预警项。
     */
    @Data
    @Accessors(chain = true)
    @Schema(description = "指标预警")
    public static class MetricAlert {

        @Schema(description = "指标编码")
        private String metric;

        @Schema(description = "指标名称")
        private String metricName;

        @Schema(description = "当前值")
        private Double currentValue;

        @Schema(description = "阈值")
        private Double threshold;

        @Schema(description = "预警级别：WARN / DANGER")
        private String level;

        @Schema(description = "提示语")
        private String message;
    }
}
