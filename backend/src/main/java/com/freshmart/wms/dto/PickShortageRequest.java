package com.freshmart.wms.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 缺货上报请求。拣货员在明细行上上报库位实物不足。
 */
@Data
@Accessors(chain = true)
@Schema(description = "缺货上报请求")
public class PickShortageRequest {

    @NotNull(message = "明细行 ID 不能为空")
    @Schema(description = "拣货明细行 ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long itemId;

    @NotNull(message = "缺货数量不能为空")
    @Min(value = 1, message = "缺货数量至少为 1")
    @Schema(description = "缺货数量", requiredMode = Schema.RequiredMode.REQUIRED, example = "3")
    private Integer shortageQty;

    @Schema(description = "缺货原因：STOCK_ZERO / STOCK_DIFF / LOCATION_WRONG / DAMAGED / EXPIRED / LOCKED / UNKNOWN",
            example = "STOCK_ZERO")
    private String reason = "STOCK_ZERO";

    @Schema(description = "拣货员工号")
    private String pickerNo;

    @Schema(description = "拣货员姓名")
    private String pickerName;

    @Schema(description = "补充说明")
    private String remark;
}
