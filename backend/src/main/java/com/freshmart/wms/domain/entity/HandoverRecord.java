package com.freshmart.wms.domain.entity;

import com.freshmart.wms.domain.enums.HandoverStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 出库交接单。
 *
 * <p>复核完成的波次按承运商归集，生成交接单，由仓库方与承运商司机
 * 共同清点件数后确认交接，订单随之置为「已出库」。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@Schema(description = "出库交接单")
public class HandoverRecord extends BaseEntity {

    @Schema(description = "交接单号", example = "HO20261015001")
    private String handoverNo;

    @Schema(description = "关联波次号（多个以逗号分隔）")
    private String waveNos;

    @Schema(description = "承运商编码")
    private String carrierCode;

    @Schema(description = "承运商名称")
    private String carrierName;

    @Schema(description = "订单数")
    private Integer orderCount = 0;

    @Schema(description = "包裹件数")
    private Integer packageCount = 0;

    @Schema(description = "总重量（kg）")
    private Double totalWeight = 0.0;

    @Schema(description = "交接状态")
    private HandoverStatus status = HandoverStatus.CREATED;

    @Schema(description = "仓库方交接人工号")
    private String handoverUserNo;

    @Schema(description = "仓库方交接人姓名")
    private String handoverUserName;

    @Schema(description = "承运商接收人姓名")
    private String receiveName;

    @Schema(description = "承运商司机车牌号")
    private String vehicleNo;

    @Schema(description = "实际交接完成时间")
    private LocalDateTime handoverTime;

    @Schema(description = "异常说明（拒收等情况）")
    private String remark;
}
