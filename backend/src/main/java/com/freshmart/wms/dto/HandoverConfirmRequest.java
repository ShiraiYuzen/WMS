package com.freshmart.wms.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 交接确认请求。承运商司机清点无误后，仓库方确认交接完成。
 */
@Data
@Accessors(chain = true)
@Schema(description = "交接确认请求")
public class HandoverConfirmRequest {

    @NotBlank(message = "交接单号不能为空")
    @Schema(description = "交接单号", requiredMode = Schema.RequiredMode.REQUIRED)
    private String handoverNo;

    @Schema(description = "承运商接收人姓名")
    private String receiveName;

    @Schema(description = "实交件数（清点结果，与系统件数不一致时记异常）")
    private Integer actualPackageCount;

    @Schema(description = "是否拒收")
    private Boolean rejected = false;

    @Schema(description = "异常说明")
    private String remark;
}
