package com.freshmart.wms.domain.enums;

import lombok.Getter;

/**
 * 复核结果状态。复核员逐单核对「应拣 vs 实拣」，据此判定。
 */
@Getter
public enum ReviewStatus implements CodedEnum {

    WAITING("WAITING", "待复核"),
    PASS("PASS", "复核通过"),
    DIFF("DIFF", "存在差异"),
    REPICKING("REPICKING", "已退回重拣");

    private final String code;
    private final String desc;

    ReviewStatus(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static ReviewStatus of(String code) {
        for (ReviewStatus v : values()) {
            if (v.code.equalsIgnoreCase(code)) {
                return v;
            }
        }
        throw new IllegalArgumentException("未知复核状态: " + code);
    }
}
