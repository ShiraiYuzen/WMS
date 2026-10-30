package com.freshmart.wms.domain.entity;

import com.freshmart.wms.domain.enums.WaveStatus;
import com.freshmart.wms.domain.enums.WaveStrategy;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 波次主表 —— 作业主线实体。
 *
 * <p>一个波次承载「一批订单」的完整作业生命周期，
 * 依次流经 拣货 → 复核 → 交接 三个环节，
 * 各环节的起止时间都记录在本表，用于效率复盘。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@Schema(description = "拣货波次")
public class Wave extends BaseEntity {

    @Schema(description = "波次号", example = "WV20261015001")
    private String waveNo;

    @Schema(description = "波次名称", example = "2026-10-15 华南区波次 01")
    private String waveName;

    @Schema(description = "生成策略")
    private WaveStrategy strategy;

    @Schema(description = "波次状态")
    private WaveStatus status = WaveStatus.CREATED;

    /* ---------------- 容量与统计 ---------------- */

    @Schema(description = "订单数")
    private Integer orderCount = 0;

    @Schema(description = "SKU 种类数（去重后）")
    private Integer skuCount = 0;

    @Schema(description = "商品总件数")
    private Integer totalQty = 0;

    @Schema(description = "涉及库位数")
    private Integer locationCount = 0;

    @Schema(description = "覆盖省份，按区域聚类时展示")
    private String provinceScope;

    /* ---------------- 人员 ---------------- */

    @Schema(description = "创建人（波次员）工号")
    private String creatorNo;

    @Schema(description = "创建人（波次员）姓名")
    private String creatorName;

    @Schema(description = "拣货员工号")
    private String pickerNo;

    @Schema(description = "拣货员姓名")
    private String pickerName;

    @Schema(description = "复核员工号")
    private String reviewerNo;

    @Schema(description = "复核员姓名")
    private String reviewerName;

    /* ---------------- 各环节时间戳（效率复盘数据源） ---------------- */

    @Schema(description = "分配拣货员时间")
    private LocalDateTime assignTime;

    @Schema(description = "拣货开始时间")
    private LocalDateTime pickStartTime;

    @Schema(description = "拣货完成时间")
    private LocalDateTime pickFinishTime;

    @Schema(description = "复核完成时间")
    private LocalDateTime reviewFinishTime;

    @Schema(description = "交接完成时间")
    private LocalDateTime handoverTime;

    /* ---------------- 派生指标（接库后可改为视图/定时任务计算） ---------------- */

    @Schema(description = "拣货耗时（秒）")
    private Long pickDurationSec;

    @Schema(description = "全流程耗时（秒）")
    private Long totalDurationSec;

    @Schema(description = "是否含缺货")
    private Boolean hasShortage = false;

    @Schema(description = "备注")
    private String remark;
}
