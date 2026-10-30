package com.freshmart.wms.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 波次分配拣货员请求。
 */
@Data
@Accessors(chain = true)
@Schema(description = "波次分配拣货员请求")
public class WaveAssignRequest {

    @NotBlank(message = "波次号不能为空")
    @Schema(description = "波次号", requiredMode = Schema.RequiredMode.REQUIRED)
    private String waveNo;

    @NotBlank(message = "请选择拣货员")
    @Schema(description = "拣货员工号", requiredMode = Schema.RequiredMode.REQUIRED)
    private String pickerNo;

    @Schema(description = "操作人（波次员）工号")
    private String operatorNo;

    @Schema(description = "操作人姓名")
    private String operatorName;
}
