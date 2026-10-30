package com.freshmart.wms.dto;

import com.freshmart.wms.common.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * 波次列表查询条件。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@Schema(description = "波次查询条件")
public class WaveQuery extends PageQuery {

    @Schema(description = "波次号（模糊匹配）")
    private String waveNo;

    @Schema(description = "波次状态")
    private String status;

    @Schema(description = "生成策略")
    private String strategy;

    @Schema(description = "拣货员工号")
    private String pickerNo;

    @Schema(description = "是否仅看在制波次")
    private Boolean onlyWorking;

    @Schema(description = "是否仅看含缺货波次")
    private Boolean onlyShortage;
}
