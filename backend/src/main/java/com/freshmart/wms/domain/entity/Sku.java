package com.freshmart.wms.domain.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * 商品主数据（SKU）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@Schema(description = "商品 SKU")
public class Sku extends BaseEntity {

    @Schema(description = "SKU 编码", example = "SKU10001")
    private String skuCode;

    @Schema(description = "商品名称", example = "红富士苹果")
    private String skuName;

    @Schema(description = "规格", example = "5kg/箱")
    private String spec;

    @Schema(description = "计量单位", example = "箱")
    private String unit;

    @Schema(description = "品类", example = "鲜果")
    private String category;

    @Schema(description = "条码")
    private String barcode;

    @Schema(description = "默认拣货库位编码", example = "A-01-02-03")
    private String defaultLocationCode;

    @Schema(description = "单件重量（kg）")
    private Double weight;

    @Schema(description = "安全库存")
    private Integer safetyStock;

    @Schema(description = "当前可用库存（内存模拟，接库后由库存表计算）")
    private Integer stockQty;

    @Schema(description = "状态：1 启用 / 0 停用")
    private Integer status = 1;
}
