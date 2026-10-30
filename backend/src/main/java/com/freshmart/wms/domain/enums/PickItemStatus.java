package com.freshmart.wms.domain.enums;

import lombok.Getter;

/**
 * 拣货明细行状态。一行 = 一个「库位 + SKU」的拣货动作。
 */
@Getter
public enum PickItemStatus implements CodedEnum {

    PENDING("PENDING", "待拣"),
    PICKED("PICKED", "已拣齐"),
    SHORTAGE("SHORTAGE", "缺货"),
    PARTIAL("PARTIAL", "部分拣货"),
    SKIPPED("SKIPPED", "已跳过");

    private final String code;
    private final String desc;

    PickItemStatus(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static PickItemStatus of(String code) {
        for (PickItemStatus v : values()) {
            if (v.code.equalsIgnoreCase(code)) {
                return v;
            }
        }
        throw new IllegalArgumentException("未知拣货明细状态: " + code);
    }
}
