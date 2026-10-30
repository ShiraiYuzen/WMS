package com.freshmart.wms.domain.entity;

import com.freshmart.wms.domain.enums.ShortageHandleType;
import com.freshmart.wms.domain.enums.ShortageReason;
import com.freshmart.wms.domain.enums.ShortageStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 缺货记录。
 *
 * <p>流程：拣货员在明细行上报缺货 → 生成本记录（{@code PENDING}）
 * → 波次员选择处理方式（拆单 / 取消明细 / 替代 / 等待补货 / 整单取消）
 * → 记录置为 {@code HANDLED}，波次继续流转。
 *
 * <p>缺货率与缺货原因是效率复盘的核心指标。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@Schema(description = "缺货记录")
public class ShortageRecord extends BaseEntity {

    @Schema(description = "缺货单号", example = "SH20261015001")
    private String shortageNo;

    @Schema(description = "所属波次号")
    private String waveNo;

    @Schema(description = "所属拣货任务号")
    private String taskNo;

    @Schema(description = "关联订单号")
    private String orderNo;

    @Schema(description = "库位编码")
    private String locationCode;

    @Schema(description = "SKU 编码")
    private String skuCode;

    @Schema(description = "SKU 名称")
    private String skuName;

    @Schema(description = "应拣数量")
    private Integer shouldQty;

    @Schema(description = "缺货数量")
    private Integer shortageQty;

    @Schema(description = "缺货原因")
    private ShortageReason reason = ShortageReason.UNKNOWN;

    @Schema(description = "上报人工号（拣货员）")
    private String reporterNo;

    @Schema(description = "上报人姓名")
    private String reporterName;

    @Schema(description = "上报时间")
    private LocalDateTime reportTime;

    @Schema(description = "处理状态")
    private ShortageStatus status = ShortageStatus.PENDING;

    @Schema(description = "处理方式（波次员填写）")
    private ShortageHandleType handleType;

    @Schema(description = "处理人工号（波次员）")
    private String handlerNo;

    @Schema(description = "处理人姓名")
    private String handlerName;

    @Schema(description = "处理时间")
    private LocalDateTime handleTime;

    @Schema(description = "处理说明")
    private String handleRemark;

    @Schema(description = "替代 SKU 编码（处理方式为替代商品时填写）")
    private String substituteSkuCode;
}
