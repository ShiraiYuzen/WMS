package com.freshmart.wms.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 订单汇聚请求。把选中的订单标记为「已汇聚入池」，等待波次员生成波次。
 */
@Data
@Accessors(chain = true)
@Schema(description = "订单汇聚请求")
public class OrderPoolRequest {

    @NotEmpty(message = "请至少选择一笔订单")
    @Schema(description = "订单号列表", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<String> orderNos;
}
