package com.freshmart.wms.domain.entity;

import com.freshmart.wms.domain.enums.OrderStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 销售订单主表。
 *
 * <p>数据来源为电商平台推送（本阶段由内存种子数据模拟）。
 * 订单汇聚环节把 {@code CREATED} 的订单标记为 {@code POOLED} 进入待波次池，
 * 波次生成环节再将其绑定到具体波次并置为 {@code WAVED}。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@Schema(description = "销售订单")
public class OrderInfo extends BaseEntity {

    @Schema(description = "订单号", example = "SO20261015000001")
    private String orderNo;

    @Schema(description = "来源平台", example = "天猫")
    private String platform;

    @Schema(description = "承运商编码", example = "SF")
    private String carrierCode;

    @Schema(description = "承运商名称", example = "顺丰速运")
    private String carrierName;

    @Schema(description = "收货人")
    private String receiverName;

    @Schema(description = "收货人电话")
    private String receiverPhone;

    @Schema(description = "收货地址")
    private String receiverAddress;

    @Schema(description = "收货省份（按区域聚类策略的关键字段）", example = "广东省")
    private String province;

    @Schema(description = "收货城市", example = "深圳市")
    private String city;

    @Schema(description = "优先级：URGENT 加急 / NORMAL 普通", example = "NORMAL")
    private String priority = "NORMAL";

    @Schema(description = "要求送达时间（按时效聚类策略的关键字段）")
    private LocalDateTime expectedDeliveryTime;

    @Schema(description = "支付时间")
    private LocalDateTime payTime;

    @Schema(description = "订单状态")
    private OrderStatus status = OrderStatus.CREATED;

    @Schema(description = "归属波次号，未分配时为空")
    private String waveNo;

    @Schema(description = "商品种类数")
    private Integer skuCount = 0;

    @Schema(description = "商品总件数")
    private Integer totalQty = 0;

    @Schema(description = "订单金额（元）")
    private Double amount = 0.0;

    @Schema(description = "买家备注")
    private String buyerRemark;
}
