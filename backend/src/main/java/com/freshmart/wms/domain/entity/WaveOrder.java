package com.freshmart.wms.domain.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * 波次-订单关联表。
 *
 * <p>虽然 {@code OrderInfo.waveNo} 已能表达归属关系，但仍单独建表，
 * 目的是承载「订单在该波次内的作业态」——例如顺序号、是否缺货挂起等，
 * 这些字段属于关联关系而非订单本身，落库后应放在关联表里。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@Schema(description = "波次订单关联")
public class WaveOrder extends BaseEntity {

    @Schema(description = "波次号")
    private String waveNo;

    @Schema(description = "订单号")
    private String orderNo;

    @Schema(description = "波次内顺序号")
    private Integer seqNo;

    @Schema(description = "该单是否被缺货挂起")
    private Boolean held = false;

    @Schema(description = "挂起原因")
    private String holdReason;
}
