package com.freshmart.wms.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 波次生成请求。
 *
 * <p>两种用法：
 * <ol>
 *   <li><b>自动生成</b>：不传 {@code orderNos}，系统从「已汇聚入池」的订单中，
 *       按 {@code strategy} 聚类切分成若干波次，受容量上限约束。</li>
 *   <li><b>指定生成</b>：传入 {@code orderNos}，只针对这批订单生成波次。</li>
 * </ol>
 */
@Data
@Accessors(chain = true)
@Schema(description = "波次生成请求")
public class WaveGenerateRequest {

    @Schema(description = "生成策略：BY_AREA / BY_CARRIER / BY_TIMELINESS / BY_ORDER_COUNT",
            example = "BY_AREA")
    private String strategy = "BY_AREA";

    @Min(value = 1, message = "单波次最大订单数不能小于 1")
    @Max(value = 200, message = "单波次最大订单数不能超过 200")
    @Schema(description = "单波次最大订单数", example = "12")
    private Integer maxOrdersPerWave = 12;

    @Schema(description = "单波次最大 SKU 种类数", example = "30")
    private Integer maxSkusPerWave = 30;

    @Schema(description = "单波次最大件数", example = "120")
    private Integer maxQtyPerWave = 120;

    @Schema(description = "指定订单号列表；为空则从订单池自动取单")
    private List<String> orderNos;

    @Schema(description = "创建人工号（波次员）")
    private String creatorNo;

    @Schema(description = "创建人姓名")
    private String creatorName;

    @Schema(description = "生成后是否立即自动分配拣货员（按当前在岗拣货员负载轮询）")
    private Boolean autoAssign = false;

    @Schema(description = "波次名称前缀", example = "2026-10-16 华南区")
    private String namePrefix;
}
