package com.freshmart.wms.domain.enums;

import lombok.Getter;

/**
 * 复核差异类型。差异率统计的原始口径即来源于此。
 */
@Getter
public enum DiffType implements CodedEnum {

    NONE("NONE", "无差异"),
    SHORT("SHORT", "少货"),
    OVER("OVER", "多货"),
    WRONG_SKU("WRONG_SKU", "错货"),
    DAMAGED("DAMAGED", "破损"),
    WRONG_ORDER("WRONG_ORDER", "串单");

    private final String code;
    private final String desc;

    DiffType(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static DiffType of(String code) {
        for (DiffType v : values()) {
            if (v.code.equalsIgnoreCase(code)) {
                return v;
            }
        }
        throw new IllegalArgumentException("未知差异类型: " + code);
    }

    /** 是否计入差错率（无差异不计入分母）。 */
    public boolean isError() {
        return this != NONE;
    }
}
