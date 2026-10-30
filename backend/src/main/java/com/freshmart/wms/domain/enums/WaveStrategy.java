package com.freshmart.wms.domain.enums;

import lombok.Getter;

/**
 * 波次生成策略。波次员在生成波次时选择，决定订单如何被聚类成一批。
 */
@Getter
public enum WaveStrategy implements CodedEnum {

    BY_AREA("BY_AREA", "按收货区域聚类",
            "同一省份/城市的订单聚成一个波次，便于后续按线路分拣与装车。"),

    BY_CARRIER("BY_CARRIER", "按承运商聚类",
            "同一快递承运商的订单聚成一个波次，交接环节只需面对一个承运商。"),

    BY_TIMELINESS("BY_TIMELINESS", "按时效优先级聚类",
            "加急/当日达等高优先级订单单独成波，保证时效订单优先出库。"),

    BY_ORDER_COUNT("BY_ORDER_COUNT", "按单量均衡聚类",
            "按顺序均匀切分成多个波次，使各拣货员工作量大致持平。");

    private final String code;
    private final String desc;
    private final String detail;

    WaveStrategy(String code, String desc, String detail) {
        this.code = code;
        this.desc = desc;
        this.detail = detail;
    }

    public static WaveStrategy of(String code) {
        for (WaveStrategy v : values()) {
            if (v.code.equalsIgnoreCase(code)) {
                return v;
            }
        }
        throw new IllegalArgumentException("未知波次策略: " + code);
    }
}
