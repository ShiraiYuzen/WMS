package com.freshmart.wms.vo;

import com.freshmart.wms.domain.entity.OrderInfo;
import com.freshmart.wms.domain.entity.OrderItem;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;

/**
 * 订单视图对象：订单主表 + 明细行 + 状态中文。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@Schema(description = "订单视图")
public class OrderVO extends OrderInfo {

    @Schema(description = "订单状态中文")
    private String statusDesc;

    @Schema(description = "优先级中文")
    private String priorityDesc;

    @Schema(description = "商品明细")
    private List<OrderItem> items = new ArrayList<>();

    @Schema(description = "是否可汇聚入池")
    private Boolean poolable = false;
}
