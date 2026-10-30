package com.freshmart.wms.vo;

import com.freshmart.wms.domain.entity.OrderInfo;
import com.freshmart.wms.domain.entity.Wave;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;

/**
 * 波次视图对象：波次主表 + 状态中文 + 订单列表 + 流程进度。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@Schema(description = "波次视图")
public class WaveVO extends Wave {

    @Schema(description = "波次状态中文")
    private String statusDesc;

    @Schema(description = "策略中文")
    private String strategyDesc;

    @Schema(description = "流程进度百分比 0-100")
    private Integer progress;

    @Schema(description = "当前所处环节：波次生成 / 拣货 / 复核 / 交接 / 完成")
    private String currentNode;

    @Schema(description = "该波次下的订单列表")
    private List<OrderInfo> orders = new ArrayList<>();
}
