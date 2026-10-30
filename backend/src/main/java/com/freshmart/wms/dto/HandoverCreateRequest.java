package com.freshmart.wms.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 出库交接单创建请求。把已完成复核的波次按承运商归集成交接单。
 */
@Data
@Accessors(chain = true)
@Schema(description = "出库交接单创建请求")
public class HandoverCreateRequest {

    @NotEmpty(message = "请至少选择一个波次")
    @Schema(description = "波次号列表", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<String> waveNos;

    @NotBlank(message = "请选择承运商")
    @Schema(description = "承运商编码", requiredMode = Schema.RequiredMode.REQUIRED, example = "SF")
    private String carrierCode;

    @Schema(description = "仓库方交接人工号")
    private String handoverUserNo;

    @Schema(description = "仓库方交接人姓名")
    private String handoverUserName;

    @Schema(description = "承运商司机车牌号")
    private String vehicleNo;

    @Schema(description = "备注")
    private String remark;
}
