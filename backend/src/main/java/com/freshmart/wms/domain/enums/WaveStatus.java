package com.freshmart.wms.domain.enums;

import lombok.Getter;

/**
 * 波次状态机（作业主线）。
 *
 * <pre>
 *   CREATED ──分配拣货员──▶ ASSIGNED ──拣货员开工──▶ PICKING
 *      └──────────────── 下发即开工 ────────────────┘
 *   PICKING ──全部明细拣完──▶ PICKED ──复核完成──▶ REVIEWED
 *   REVIEWED ──完成交接──▶ HANDOVERED ──归档──▶ CLOSED
 * </pre>
 */
@Getter
public enum WaveStatus implements CodedEnum {

    CREATED("CREATED", "待分配"),
    ASSIGNED("ASSIGNED", "已分配拣货员"),
    PICKING("PICKING", "拣货中"),
    PICKED("PICKED", "拣货完成"),
    REVIEWING("REVIEWING", "复核中"),
    REVIEWED("REVIEWED", "复核完成"),
    HANDOVERED("HANDOVERED", "已出库交接"),
    CLOSED("CLOSED", "已归档"),
    CANCELLED("CANCELLED", "已取消");

    private final String code;
    private final String desc;

    WaveStatus(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static WaveStatus of(String code) {
        for (WaveStatus v : values()) {
            if (v.code.equalsIgnoreCase(code)) {
                return v;
            }
        }
        throw new IllegalArgumentException("未知波次状态: " + code);
    }

    /** 是否为「作业进行中」的状态，用于看板统计在制波次。 */
    public boolean isWorking() {
        return this == ASSIGNED || this == PICKING || this == REVIEWING;
    }
}
