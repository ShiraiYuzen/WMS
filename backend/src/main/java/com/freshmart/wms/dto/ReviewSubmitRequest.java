package com.freshmart.wms.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;

/**
 * 复核提交请求。复核员核对完一单后提交结果。
 *
 * <p>若无差异，{@code diffItems} 传空数组、{@code diffType} 传 NONE；
 * 若有差异，逐条列出差异明细，系统据此累计差错率。
 */
@Data
@Accessors(chain = true)
@Schema(description = "复核提交请求")
public class ReviewSubmitRequest {

    @NotBlank(message = "复核单号不能为空")
    @Schema(description = "复核单号", requiredMode = Schema.RequiredMode.REQUIRED)
    private String reviewNo;

    @Schema(description = "实拣总件数（复核员清点结果）")
    private Integer actualQty;

    @Schema(description = "复核员工号")
    private String reviewerNo;

    @Schema(description = "复核员姓名")
    private String reviewerName;

    /* ---------------- 打包信息 ---------------- */

    @Schema(description = "包材类型：纸箱 / 气泡袋 / 保温箱", example = "纸箱")
    private String packMaterial = "纸箱";

    @Schema(description = "包裹件数", example = "1")
    private Integer packageCount = 1;

    @Schema(description = "包裹重量（kg）", example = "3.5")
    private Double weight;

    @Schema(description = "备注")
    private String remark;

    @Valid
    @Schema(description = "差异明细列表；无差异时为空数组")
    private List<DiffItemDTO> diffItems = new ArrayList<>();

    /**
     * 差异明细行。
     */
    @Data
    @Accessors(chain = true)
    @Schema(description = "差异明细")
    public static class DiffItemDTO {

        @Schema(description = "SKU 编码")
        private String skuCode;

        @Schema(description = "SKU 名称")
        private String skuName;

        @Schema(description = "差异类型：SHORT 少货 / OVER 多货 / WRONG_SKU 错货 / DAMAGED 破损 / WRONG_ORDER 串单")
        private String diffType;

        @Schema(description = "应拣数量")
        private Integer shouldQty;

        @Schema(description = "实拣数量")
        private Integer actualQty;

        @Schema(description = "责任环节：PICK / WAVE / DATA")
        private String responsibleStage;

        @Schema(description = "责任人工号")
        private String responsibleNo;

        @Schema(description = "差异说明")
        private String remark;
    }
}
