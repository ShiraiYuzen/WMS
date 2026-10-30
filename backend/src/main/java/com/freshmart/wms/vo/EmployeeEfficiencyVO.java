package com.freshmart.wms.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 效率复盘 —— 人员效率。
 */
@Data
@Accessors(chain = true)
@Schema(description = "人员效率")
public class EmployeeEfficiencyVO {

    @Schema(description = "工号")
    private String employeeNo;

    @Schema(description = "姓名")
    private String employeeName;

    @Schema(description = "角色中文")
    private String roleDesc;

    @Schema(description = "所属班组")
    private String team;

    /* ---------------- 拣货员指标 ---------------- */

    @Schema(description = "完成任务数")
    private Long taskCount = 0L;

    @Schema(description = "拣货件数")
    private Long pickedQty = 0L;

    @Schema(description = "拣货效率（件/小时）")
    private Double qtyPerHour = 0.0;

    @Schema(description = "平均单任务耗时（秒）")
    private Double avgDurationSec = 0.0;

    @Schema(description = "行走库位数")
    private Long locationCount = 0L;

    /* ---------------- 复核员指标 ---------------- */

    @Schema(description = "复核单数")
    private Long reviewCount = 0L;

    @Schema(description = "复核差错单数")
    private Long errorCount = 0L;

    @Schema(description = "个人差错率")
    private Double errorRate = 0.0;

    @Schema(description = "复核效率（单/小时）")
    private Double orderPerHour = 0.0;

    @Schema(description = "综合评分 0-100")
    private Double score = 0.0;
}
