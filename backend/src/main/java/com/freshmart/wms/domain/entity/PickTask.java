package com.freshmart.wms.domain.entity;

import com.freshmart.wms.domain.enums.PickTaskStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 拣货任务。一个波次对应一个拣货任务（一单到底模式）。
 *
 * <p>任务下挂若干 {@link PickItem}，明细行已按库位路径排序，
 * 拣货员按顺序逐行扫码确认即可。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@Schema(description = "拣货任务")
public class PickTask extends BaseEntity {

    @Schema(description = "任务号", example = "PK20261015001")
    private String taskNo;

    @Schema(description = "所属波次号")
    private String waveNo;

    @Schema(description = "拣货员工号")
    private String pickerNo;

    @Schema(description = "拣货员姓名")
    private String pickerName;

    @Schema(description = "任务状态")
    private PickTaskStatus status = PickTaskStatus.WAITING;

    @Schema(description = "明细行数（去重后的 库位+SKU 组合数）")
    private Integer itemCount = 0;

    @Schema(description = "已完成行数")
    private Integer finishedCount = 0;

    @Schema(description = "涉及库位数")
    private Integer locationCount = 0;

    @Schema(description = "应拣总件数")
    private Integer shouldQty = 0;

    @Schema(description = "实拣总件数")
    private Integer actualQty = 0;

    @Schema(description = "缺货件数")
    private Integer shortageQty = 0;

    @Schema(description = "涉及订单数")
    private Integer orderCount = 0;

    @Schema(description = "开始时间")
    private LocalDateTime startTime;

    @Schema(description = "完成时间")
    private LocalDateTime finishTime;

    @Schema(description = "耗时（秒）")
    private Long durationSec;

    @Schema(description = "拣货路径（库位序列，便于现场核对）")
    private String pathText;

    @Schema(description = "备注")
    private String remark;
}
