package com.freshmart.wms.domain.enums;

import lombok.Getter;

/**
 * 订单状态机。
 *
 * <pre>
 *   CREATED ──汇聚──▶ POOLED ──生成波次──▶ WAVED ──拣货完成──▶ PICKED
 *                                                              │
 *                                                        复核完成 ▼
 *                                                          REVIEWED ──交接──▶ OUTBOUND
 *   CREATED / POOLED ──取消──▶ CANCELLED
 * </pre>
 */
@Getter
public enum OrderStatus implements CodedEnum {

    CREATED("CREATED", "新建待汇聚"),
    POOLED("POOLED", "已汇聚入池"),
    WAVED("WAVED", "已分配波次"),
    PICKED("PICKED", "已拣货"),
    REVIEWED("REVIEWED", "已复核"),
    OUTBOUND("OUTBOUND", "已出库"),
    CANCELLED("CANCELLED", "已取消");

    private final String code;
    private final String desc;

    OrderStatus(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static OrderStatus of(String code) {
        for (OrderStatus v : values()) {
            if (v.code.equalsIgnoreCase(code)) {
                return v;
            }
        }
        throw new IllegalArgumentException("未知订单状态: " + code);
    }

    /** 是否还停留在「未进入波次」阶段。 */
    public boolean isPoolable() {
        return this == CREATED || this == POOLED;
    }
}
