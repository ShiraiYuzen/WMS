package com.freshmart.wms.domain.enums;

import lombok.Getter;

/**
 * 出库交接单状态。
 */
@Getter
public enum HandoverStatus implements CodedEnum {

    CREATED("CREATED", "待交接"),
    CONFIRMED("CONFIRMED", "已交接"),
    REJECTED("REJECTED", "承运商拒收"),
    CANCELLED("CANCELLED", "已作废");

    private final String code;
    private final String desc;

    HandoverStatus(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static HandoverStatus of(String code) {
        for (HandoverStatus v : values()) {
            if (v.code.equalsIgnoreCase(code)) {
                return v;
            }
        }
        throw new IllegalArgumentException("未知交接状态: " + code);
    }
}
