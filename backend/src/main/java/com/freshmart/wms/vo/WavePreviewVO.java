package com.freshmart.wms.vo;

import com.freshmart.wms.domain.entity.OrderInfo;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;

/**
 * 波次生成预览结果。
 *
 * <p>波次员点「生成波次」前先调预览接口，看清系统会切成几波、每波多少单，
 * 确认无误后再落库生成，避免误操作产生垃圾波次。
 */
@Data
@Accessors(chain = true)
@Schema(description = "波次生成预览")
public class WavePreviewVO {

    @Schema(description = "使用的生成策略")
    private String strategy;

    @Schema(description = "策略中文说明")
    private String strategyDesc;

    @Schema(description = "可生成波次数")
    private Integer waveCount;

    @Schema(description = "参与订单总数")
    private Integer totalOrders;

    @Schema(description = "参与商品总件数")
    private Integer totalQty;

    @Schema(description = "分组明细")
    private List<GroupPreview> groups = new ArrayList<>();

    /**
     * 单个分组预览（= 一个待生成波次）。
     */
    @Data
    @Accessors(chain = true)
    @Schema(description = "波次分组预览")
    public static class GroupPreview {

        @Schema(description = "分组键，例如省份名 / 承运商名")
        private String groupKey;

        @Schema(description = "建议波次名称")
        private String suggestWaveName;

        @Schema(description = "订单数")
        private Integer orderCount;

        @Schema(description = "SKU 种类数")
        private Integer skuCount;

        @Schema(description = "商品总件数")
        private Integer totalQty;

        @Schema(description = "涉及库位数")
        private Integer locationCount;

        @Schema(description = "是否超出容量上限")
        private Boolean overCapacity = false;

        @Schema(description = "该组订单")
        private List<OrderInfo> orders = new ArrayList<>();
    }
}
