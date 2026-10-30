package com.freshmart.wms.domain.enums;

import lombok.Getter;

/**
 * 缺货处理方式。拣货员上报缺货后，由波次员选择处理策略。
 */
@Getter
public enum ShortageHandleType implements CodedEnum {

    SPLIT("SPLIT", "拆单出库",
            "缺货部分拆分为新订单挂起，其余商品正常出库。"),

    CANCEL_ITEM("CANCEL_ITEM", "取消该明细",
            "直接取消缺货商品行，订单其余部分继续流转。"),

    SUBSTITUTE("SUBSTITUTE", "替代商品",
            "用同品类可替代 SKU 顶替，需人工确认替代关系。"),

    WAIT_REPLENISH("WAIT_REPLENISH", "等待补货",
            "挂起该波次，等待补货上架后继续拣货。"),

    CANCEL_ORDER("CANCEL_ORDER", "整单取消",
            "缺货导致整单无法履约，取消订单并通知客户。");

    private final String code;
    private final String desc;
    private final String detail;

    ShortageHandleType(String code, String desc, String detail) {
        this.code = code;
        this.desc = desc;
        this.detail = detail;
    }

    public static ShortageHandleType of(String code) {
        for (ShortageHandleType v : values()) {
            if (v.code.equalsIgnoreCase(code)) {
                return v;
            }
        }
        throw new IllegalArgumentException("未知缺货处理方式: " + code);
    }
}
