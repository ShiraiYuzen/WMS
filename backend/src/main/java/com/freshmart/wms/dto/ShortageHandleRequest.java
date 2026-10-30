package com.freshmart.wms.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 缺货处理请求。波次员对一条缺货记录选择处理方式。
 */
@Data
@Accessors(chain = true)
@Schema(description = "缺货处理请求")
public class ShortageHandleRequest {

    @NotNull(message = "缺货记录 ID 不能为空")
    @Schema(description = "缺货记录 ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long id;

    @NotBlank(message = "请选择处理方式")
    @Schema(description = "处理方式：SPLIT / CANCEL_ITEM / SUBSTITUTE / WAIT_REPLENISH / CANCEL_ORDER",
            requiredMode = Schema.RequiredMode.REQUIRED, example = "SPLIT")
    private String handleType;

    @Schema(description = "替代 SKU 编码（处理方式为 SUBSTITUTE 时必填）")
    private String substituteSkuCode;

    @Schema(description = "处理人工号（波次员）")
    private String handlerNo;

    @Schema(description = "处理人姓名")
    private String handlerName;

    @Schema(description = "处理说明")
    private String handleRemark;
}
