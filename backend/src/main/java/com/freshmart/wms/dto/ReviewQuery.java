package com.freshmart.wms.dto;

import com.freshmart.wms.common.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * 复核记录查询条件。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@Schema(description = "复核记录查询条件")
public class ReviewQuery extends PageQuery {

    @Schema(description = "复核单号（模糊匹配）")
    private String reviewNo;

    @Schema(description = "波次号")
    private String waveNo;

    @Schema(description = "订单号")
    private String orderNo;

    @Schema(description = "复核状态")
    private String status;

    @Schema(description = "差异类型")
    private String diffType;

    @Schema(description = "复核员工号")
    private String reviewerNo;
}
