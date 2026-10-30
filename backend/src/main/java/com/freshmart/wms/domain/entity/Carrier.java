package com.freshmart.wms.domain.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * 快递承运商。出库交接环节的对手方。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@Schema(description = "快递承运商")
public class Carrier extends BaseEntity {

    @Schema(description = "承运商编码", example = "SF")
    private String carrierCode;

    @Schema(description = "承运商名称", example = "顺丰速运")
    private String carrierName;

    @Schema(description = "交接联系人")
    private String contactName;

    @Schema(description = "交接联系电话")
    private String contactPhone;

    @Schema(description = "每日揽收时间", example = "18:00")
    private String dailyPickupTime;

    @Schema(description = "状态：1 启用 / 0 停用")
    private Integer status = 1;
}
