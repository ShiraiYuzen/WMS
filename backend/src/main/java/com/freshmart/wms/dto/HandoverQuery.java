package com.freshmart.wms.dto;

import com.freshmart.wms.common.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * 出库交接单查询条件。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@Schema(description = "交接单查询条件")
public class HandoverQuery extends PageQuery {

    @Schema(description = "交接单号（模糊匹配）")
    private String handoverNo;

    @Schema(description = "承运商编码")
    private String carrierCode;

    @Schema(description = "交接状态")
    private String status;
}
