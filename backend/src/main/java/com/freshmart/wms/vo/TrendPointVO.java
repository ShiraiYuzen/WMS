package com.freshmart.wms.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 效率复盘 —— 趋势数据点。
 */
@Data
@Accessors(chain = true)
@Schema(description = "趋势数据点")
public class TrendPointVO {

    @Schema(description = "时间标签，例如 2026-10-16 或 2026-W42")
    private String label;

    @Schema(description = "波次数")
    private Long waveCount = 0L;

    @Schema(description = "订单数")
    private Long orderCount = 0L;

    @Schema(description = "拣货件数")
    private Long pickedQty = 0L;

    @Schema(description = "拣货效率（件/小时）")
    private Double pickEfficiency = 0.0;

    @Schema(description = "差错率")
    private Double errorRate = 0.0;

    @Schema(description = "缺货件数")
    private Long shortageQty = 0L;

    @Schema(description = "平均波次耗时（秒）")
    private Double avgDurationSec = 0.0;
}
