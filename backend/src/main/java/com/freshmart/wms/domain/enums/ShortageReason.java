package com.freshmart.wms.domain.enums;

import lombok.Getter;

/**
 * 缺货原因分类。用于效率复盘时定位缺货根因。
 */
@Getter
public enum ShortageReason implements CodedEnum {

    STOCK_ZERO("STOCK_ZERO", "库位实物为零"),
    STOCK_DIFF("STOCK_DIFF", "账实不符"),
    LOCATION_WRONG("LOCATION_WRONG", "库位商品错放"),
    DAMAGED("DAMAGED", "商品破损不可售"),
    EXPIRED("EXPIRED", "临期/过期"),
    LOCKED("LOCKED", "已被其他波次锁定"),
    UNKNOWN("UNKNOWN", "原因待查");

    private final String code;
    private final String desc;

    ShortageReason(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static ShortageReason of(String code) {
        for (ShortageReason v : values()) {
            if (v.code.equalsIgnoreCase(code)) {
                return v;
            }
        }
        return UNKNOWN;
    }
}
