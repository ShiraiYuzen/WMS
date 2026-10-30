package com.freshmart.wms.domain.entity;

import com.freshmart.wms.domain.enums.DiffType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * 复核差异明细行。
 *
 * <p>一条 {@link ReviewRecord} 可对应多条差异明细（例如同时存在少货与错货）。
 * 差错率统计按「差异明细条数 / 复核订单数」计算。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@Schema(description = "复核差异明细")
public class ReviewDiffItem extends BaseEntity {

    @Schema(description = "复核单号")
    private String reviewNo;

    @Schema(description = "波次号")
    private String waveNo;

    @Schema(description = "订单号")
    private String orderNo;

    @Schema(description = "SKU 编码")
    private String skuCode;

    @Schema(description = "SKU 名称")
    private String skuName;

    @Schema(description = "差异类型")
    private DiffType diffType;

    @Schema(description = "应拣数量")
    private Integer shouldQty;

    @Schema(description = "实拣数量")
    private Integer actualQty;

    @Schema(description = "差异数量（实拣 - 应拣，负数表示少货）")
    private Integer diffQty;

    @Schema(description = "责任环节：PICK 拣货 / WAVE 波次 / DATA 基础数据")
    private String responsibleStage;

    @Schema(description = "责任人工号")
    private String responsibleNo;

    @Schema(description = "差异说明")
    private String remark;
}
