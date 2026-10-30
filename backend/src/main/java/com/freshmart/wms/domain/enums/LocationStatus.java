package com.freshmart.wms.domain.enums;

import lombok.Getter;

/**
 * 库位状态。
 */
@Getter
public enum LocationStatus implements CodedEnum {

    ENABLED("ENABLED", "可用"),
    LOCKED("LOCKED", "已锁定"),
    FULL("FULL", "已满"),
    DISABLED("DISABLED", "停用");

    private final String code;
    private final String desc;

    LocationStatus(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static LocationStatus of(String code) {
        for (LocationStatus v : values()) {
            if (v.code.equalsIgnoreCase(code)) {
                return v;
            }
        }
        throw new IllegalArgumentException("未知库位状态: " + code);
    }
}
