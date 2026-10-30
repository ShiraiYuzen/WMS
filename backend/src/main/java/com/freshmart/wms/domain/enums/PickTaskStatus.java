package com.freshmart.wms.domain.enums;

import lombok.Getter;

/**
 * 拣货任务状态。
 */
@Getter
public enum PickTaskStatus implements CodedEnum {

    WAITING("WAITING", "待领取"),
    PICKING("PICKING", "拣货中"),
    FINISHED("FINISHED", "已完成"),
    EXCEPTION("EXCEPTION", "有缺货待处理"),
    CANCELLED("CANCELLED", "已取消");

    private final String code;
    private final String desc;

    PickTaskStatus(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static PickTaskStatus of(String code) {
        for (PickTaskStatus v : values()) {
            if (v.code.equalsIgnoreCase(code)) {
                return v;
            }
        }
        throw new IllegalArgumentException("未知拣货任务状态: " + code);
    }
}
