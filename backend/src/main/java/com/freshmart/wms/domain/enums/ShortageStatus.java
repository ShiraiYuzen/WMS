package com.freshmart.wms.domain.enums;

import lombok.Getter;

/**
 * 缺货记录处理状态。
 */
@Getter
public enum ShortageStatus implements CodedEnum {

    PENDING("PENDING", "待波次员处理"),
    HANDLED("HANDLED", "已处理"),
    CLOSED("CLOSED", "已归档");

    private final String code;
    private final String desc;

    ShortageStatus(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static ShortageStatus of(String code) {
        for (ShortageStatus v : values()) {
            if (v.code.equalsIgnoreCase(code)) {
                return v;
            }
        }
        throw new IllegalArgumentException("未知缺货状态: " + code);
    }
}
