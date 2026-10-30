package com.freshmart.wms.dto;

import com.freshmart.wms.common.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * 拣货任务查询条件。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@Schema(description = "拣货任务查询条件")
public class PickTaskQuery extends PageQuery {

    @Schema(description = "任务号（模糊匹配）")
    private String taskNo;

    @Schema(description = "波次号")
    private String waveNo;

    @Schema(description = "拣货员工号")
    private String pickerNo;

    @Schema(description = "任务状态")
    private String status;
}
