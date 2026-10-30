package com.freshmart.wms.dto;

import com.freshmart.wms.common.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.time.LocalDate;

/**
 * 订单列表查询条件。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@Schema(description = "订单查询条件")
public class OrderQuery extends PageQuery {

    @Schema(description = "订单号（模糊匹配）")
    private String orderNo;

    @Schema(description = "来源平台")
    private String platform;

    @Schema(description = "承运商编码")
    private String carrierCode;

    @Schema(description = "订单状态")
    private String status;

    @Schema(description = "收货省份")
    private String province;

    @Schema(description = "优先级：URGENT / NORMAL")
    private String priority;

    @Schema(description = "下单开始日期")
    private LocalDate startDate;

    @Schema(description = "下单结束日期")
    private LocalDate endDate;

    @Schema(description = "归属波次号")
    private String waveNo;
}
