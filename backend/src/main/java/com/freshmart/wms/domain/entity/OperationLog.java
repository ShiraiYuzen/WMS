package com.freshmart.wms.domain.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * 作业操作日志。
 *
 * <p>记录每一次状态流转（谁、在什么时间、对哪张单、做了什么），
 * 用于作业追溯与效率复盘的明细佐证。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@Schema(description = "作业操作日志")
public class OperationLog extends BaseEntity {

    @Schema(description = "业务类型：ORDER / WAVE / PICK / REVIEW / HANDOVER / SHORTAGE")
    private String bizType;

    @Schema(description = "业务单号")
    private String bizNo;

    @Schema(description = "操作动作编码，例如 WAVE_CREATE、PICK_CONFIRM")
    private String action;

    @Schema(description = "操作动作描述")
    private String actionDesc;

    @Schema(description = "操作人工号")
    private String operatorNo;

    @Schema(description = "操作人姓名")
    private String operatorName;

    @Schema(description = "操作人角色")
    private String operatorRole;

    @Schema(description = "操作明细（JSON 字符串或文本描述）")
    private String detail;

    @Schema(description = "操作时间")
    private java.time.LocalDateTime operateTime;
}
