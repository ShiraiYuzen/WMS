package com.freshmart.wms.domain.enums;

import lombok.Getter;

/**
 * 员工角色。对应本系统三类作业角色 + 管理员。
 */
@Getter
public enum EmployeeRole implements CodedEnum {

    WAVE_OPERATOR("WAVE_OPERATOR", "波次员"),
    PICKER("PICKER", "拣货员"),
    REVIEWER("REVIEWER", "复核员"),
    ADMIN("ADMIN", "仓库主管");

    private final String code;
    private final String desc;

    EmployeeRole(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static EmployeeRole of(String code) {
        for (EmployeeRole v : values()) {
            if (v.code.equalsIgnoreCase(code)) {
                return v;
            }
        }
        throw new IllegalArgumentException("未知员工角色: " + code);
    }
}
