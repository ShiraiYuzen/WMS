package com.freshmart.wms.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;

/**
 * 效率复盘 —— 差错率统计。
 */
@Data
@Accessors(chain = true)
@Schema(description = "差错率统计")
public class ErrorRateVO {

    @Schema(description = "复核单总数")
    private Long reviewTotal = 0L;

    @Schema(description = "通过单数")
    private Long passCount = 0L;

    @Schema(description = "差异单数")
    private Long diffCount = 0L;

    @Schema(description = "总体差错率")
    private Double errorRate = 0.0;

    @Schema(description = "差异件数合计")
    private Long diffQtyTotal = 0L;

    @Schema(description = "按差异类型分布")
    private List<TypeDistribution> typeDistribution = new ArrayList<>();

    @Schema(description = "按责任环节分布")
    private List<TypeDistribution> stageDistribution = new ArrayList<>();

    @Schema(description = "差错率最高的 TOP 人员")
    private List<EmployeeEfficiencyVO> topErrorEmployees = new ArrayList<>();

    /**
     * 分布统计项。
     */
    @Data
    @Accessors(chain = true)
    @Schema(description = "分布统计项")
    public static class TypeDistribution {

        @Schema(description = "类型编码")
        private String code;

        @Schema(description = "类型中文")
        private String name;

        @Schema(description = "数量")
        private Long count = 0L;

        @Schema(description = "占比 0-1")
        private Double ratio = 0.0;
    }
}
