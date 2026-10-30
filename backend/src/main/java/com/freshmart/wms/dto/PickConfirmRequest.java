package com.freshmart.wms.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 拣货确认请求。拣货员在某一明细行上确认实拣数量。
 */
@Data
@Accessors(chain = true)
@Schema(description = "拣货确认请求")
public class PickConfirmRequest {

    @NotNull(message = "明细行 ID 不能为空")
    @Schema(description = "拣货明细行 ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long itemId;

    @NotNull(message = "实拣数量不能为空")
    @Min(value = 0, message = "实拣数量不能为负数")
    @Schema(description = "实拣数量", requiredMode = Schema.RequiredMode.REQUIRED, example = "5")
    private Integer actualQty;

    @Schema(description = "拣货员工号")
    private String pickerNo;

    @Schema(description = "备注")
    private String remark;
}
