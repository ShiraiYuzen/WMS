package com.freshmart.wms.vo;

import com.freshmart.wms.domain.entity.PickItem;
import com.freshmart.wms.domain.entity.PickTask;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;

/**
 * 拣货任务视图对象：任务主表 + 明细行 + 状态中文。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@Schema(description = "拣货任务视图")
public class PickTaskVO extends PickTask {

    @Schema(description = "任务状态中文")
    private String statusDesc;

    @Schema(description = "完成进度百分比 0-100")
    private Integer progress;

    @Schema(description = "拣货明细行（已按库位路径排序）")
    private List<PickItem> items = new ArrayList<>();
}
