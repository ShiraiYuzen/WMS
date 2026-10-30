package com.example.wms.common;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 枚举字典（与 A 前端 mock/db.js 的 ENUMS 完全一致）。
 * - desc(group, code)：取中文描述，用于给记录补充 statusDesc/strategyDesc 等。
 * - all()：/base/enums 接口返回。
 */
public class Dict {

    private static final Map<String, Map<String, String>> ALL = new LinkedHashMap<>();
    private static final Map<String, Map<String, String>> DETAIL = new LinkedHashMap<>();

    static {
        put("orderStatus", new String[][]{
                {"CREATED", "新建待汇聚"}, {"POOLED", "已汇聚入池"}, {"WAVED", "已分配波次"},
                {"PICKED", "已拣货"}, {"REVIEWED", "已复核"}, {"OUTBOUND", "已出库"}, {"CANCELLED", "已取消"}});
        put("waveStatus", new String[][]{
                {"CREATED", "待分配"}, {"ASSIGNED", "已分配拣货员"}, {"PICKING", "拣货中"}, {"PICKED", "拣货完成"},
                {"REVIEWING", "复核中"}, {"REVIEWED", "复核完成"}, {"HANDOVERED", "已出库交接"}, {"CLOSED", "已归档"}, {"CANCELLED", "已取消"}});
        put("waveStrategy", new String[][]{
                {"BY_AREA", "按收货区域聚类"}, {"BY_CARRIER", "按承运商聚类"}, {"BY_TIMELINESS", "按时效优先级聚类"}, {"BY_ORDER_COUNT", "按单量均衡聚类"}},
                new String[][]{
                        {"BY_AREA", "同一省份/城市的订单聚成一个波次，便于后续按线路分拣与装车。"},
                        {"BY_CARRIER", "同一快递承运商的订单聚成一个波次，交接环节只需面对一个承运商。"},
                        {"BY_TIMELINESS", "加急/当日达等高优先级订单单独成波，保证时效订单优先出库。"},
                        {"BY_ORDER_COUNT", "按顺序均匀切分成多个波次，使各拣货员工作量大致持平。"}});
        put("pickTaskStatus", new String[][]{
                {"WAITING", "待领取"}, {"PICKING", "拣货中"}, {"FINISHED", "已完成"}, {"EXCEPTION", "有缺货待处理"}, {"CANCELLED", "已取消"}});
        put("pickItemStatus", new String[][]{
                {"PENDING", "待拣"}, {"PICKED", "已拣齐"}, {"SHORTAGE", "缺货"}, {"PARTIAL", "部分拣货"}, {"SKIPPED", "已跳过"}});
        put("reviewStatus", new String[][]{
                {"WAITING", "待复核"}, {"PASS", "复核通过"}, {"DIFF", "存在差异"}, {"REPICKING", "已退回重拣"}});
        put("diffType", new String[][]{
                {"NONE", "无差异"}, {"SHORT", "少货"}, {"OVER", "多货"}, {"WRONG_SKU", "错货"}, {"DAMAGED", "破损"}, {"WRONG_ORDER", "串单"}});
        put("shortageStatus", new String[][]{
                {"PENDING", "待波次员处理"}, {"HANDLED", "已处理"}, {"CLOSED", "已归档"}});
        put("shortageReason", new String[][]{
                {"STOCK_ZERO", "库位实物为零"}, {"STOCK_DIFF", "账实不符"}, {"LOCATION_WRONG", "库位商品错放"},
                {"DAMAGED", "商品破损不可售"}, {"EXPIRED", "临期/过期"}, {"LOCKED", "已被其他波次锁定"}, {"UNKNOWN", "原因待查"}});
        put("shortageHandleType", new String[][]{
                {"SPLIT", "拆单出库"}, {"CANCEL_ITEM", "取消该明细"}, {"SUBSTITUTE", "替代商品"},
                {"WAIT_REPLENISH", "等待补货"}, {"CANCEL_ORDER", "整单取消"}});
        put("handoverStatus", new String[][]{
                {"CREATED", "待交接"}, {"CONFIRMED", "已交接"}, {"REJECTED", "承运商拒收"}, {"CANCELLED", "已作废"}});
        put("employeeRole", new String[][]{
                {"WAVE_OPERATOR", "波次员"}, {"PICKER", "拣货员"}, {"REVIEWER", "复核员"}, {"ADMIN", "仓库主管"}});
        put("locationStatus", new String[][]{
                {"ENABLED", "可用"}, {"LOCKED", "已锁定"}, {"FULL", "已满"}, {"DISABLED", "停用"}});
        put("priority", new String[][]{{"URGENT", "加急"}, {"NORMAL", "普通"}});
        put("platform", new String[][]{
                {"天猫", "天猫"}, {"京东", "京东"}, {"拼多多", "拼多多"}, {"抖音商城", "抖音商城"}, {"美团优选", "美团优选"}});
        put("packMaterial", new String[][]{{"纸箱", "纸箱"}, {"气泡袋", "气泡袋"}, {"保温箱", "保温箱"}});
        put("granularity", new String[][]{{"DAY", "按天"}, {"WEEK", "按周"}});
    }

    private static void put(String group, String[][] arr) {
        Map<String, String> m = new LinkedHashMap<>();
        for (String[] e : arr) m.put(e[0], e[1]);
        ALL.put(group, m);
    }

    private static void put(String group, String[][] arr, String[][] det) {
        put(group, arr);
        Map<String, String> d = new LinkedHashMap<>();
        for (String[] e : det) d.put(e[0], e[1]);
        DETAIL.put(group, d);
    }

    public static String desc(String group, String code) {
        if (code == null) return null;
        Map<String, String> m = ALL.get(group);
        return m == null ? code : m.getOrDefault(code, code);
    }

    public static List<Map<String, String>> list(String group) {
        Map<String, String> m = ALL.get(group);
        if (m == null) return new ArrayList<>();
        List<Map<String, String>> r = new ArrayList<>();
        for (Map.Entry<String, String> e : m.entrySet()) {
            Map<String, String> o = new LinkedHashMap<>();
            o.put("code", e.getKey());
            o.put("desc", e.getValue());
            String det = DETAIL.getOrDefault(group, Collections.emptyMap()).get(e.getKey());
            if (det != null) o.put("detail", det);
            r.add(o);
        }
        return r;
    }

    public static Map<String, List<Map<String, String>>> all() {
        Map<String, List<Map<String, String>>> r = new LinkedHashMap<>();
        for (String g : ALL.keySet()) r.put(g, list(g));
        return r;
    }
}
