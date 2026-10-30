package com.freshmart.wms.dto;

import com.freshmart.wms.common.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * 缺货记录查询条件。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@Schema(description = "缺货记录查询条件")
public class ShortageQuery extends PageQuery {

    @Schema(description = "缺货单号（模糊匹配）")
    private String shortageNo;

    @Schema(description = "波次号")
    private String waveNo;

    @Schema(description = "SKU 编码")
    private String skuCode;

    @Schema(description = "处理状态：PENDING / HANDLED / CLOSED")
    private String status;

    @Schema(description = "缺货原因")
    private String reason;
}
