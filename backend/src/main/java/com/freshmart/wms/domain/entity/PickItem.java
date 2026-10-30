package com.freshmart.wms.domain.entity;

import com.freshmart.wms.domain.enums.PickItemStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 拣货明细行。一行 = 一个「库位 + SKU」的拣货动作。
 *
 * <p>这是波次拣货相对「一单一拣」的效率来源：
 * 波次内多个订单若命中同一库位的同一 SKU，会合并成一行，
 * 拣货员只跑一次库位就能满足多个订单。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@Schema(description = "拣货明细行")
public class PickItem extends BaseEntity {

    @Schema(description = "所属任务号")
    private String taskNo;

    @Schema(description = "所属波次号")
    private String waveNo;

    @Schema(description = "拣货顺序号，按库位路径升序")
    private Integer seqNo;

    @Schema(description = "库位编码")
    private String locationCode;

    @Schema(description = "SKU 编码")
    private String skuCode;

    @Schema(description = "SKU 名称")
    private String skuName;

    @Schema(description = "规格")
    private String spec;

    @Schema(description = "单位")
    private String unit;

    @Schema(description = "应拣数量（波次内合计）")
    private Integer shouldQty;

    @Schema(description = "实拣数量")
    private Integer actualQty = 0;

    @Schema(description = "缺货数量")
    private Integer shortageQty = 0;

    @Schema(description = "明细状态")
    private PickItemStatus status = PickItemStatus.PENDING;

    @Schema(description = "命中的订单号，逗号分隔（用于复核时按单分货）")
    private String relatedOrderNos;

    @Schema(description = "确认拣货时间")
    private LocalDateTime scanTime;

    @Schema(description = "备注")
    private String remark;
}
