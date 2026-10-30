package com.freshmart.wms.domain.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * 订单商品明细行。
 *
 * <p>一行 = 订单中的一种商品。波次生成时，同一波次下所有订单的明细行
 * 会按「库位 + SKU」合并，形成拣货明细，这是提升拣货效率的核心逻辑。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@Schema(description = "订单商品明细")
public class OrderItem extends BaseEntity {

    @Schema(description = "所属订单号")
    private String orderNo;

    @Schema(description = "行号")
    private Integer lineNo;

    @Schema(description = "SKU 编码")
    private String skuCode;

    @Schema(description = "SKU 名称")
    private String skuName;

    @Schema(description = "商品规格")
    private String spec;

    @Schema(description = "计量单位")
    private String unit;

    @Schema(description = "下单数量")
    private Integer qty;

    @Schema(description = "已拣数量")
    private Integer pickedQty = 0;

    @Schema(description = "拣货库位编码")
    private String locationCode;

    @Schema(description = "单价（元）")
    private Double price;

    @Schema(description = "行小计（元）")
    private Double amount;

    @Schema(description = "备注，例如缺货取消、替代说明")
    private String remark;
}
