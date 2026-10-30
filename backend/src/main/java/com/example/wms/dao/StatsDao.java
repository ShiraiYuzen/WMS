package com.example.wms.dao;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

/**
 * 效率复盘 / 统计分析聚合查询。
 */
@Repository
public class StatsDao {

    private final JdbcTemplate jdbc;

    public StatsDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 拣货员效率：波次数、订单数、件数、平均耗时（分钟）、总耗时 */
    public List<Map<String, Object>> pickerEfficiency() {
        return jdbc.queryForList(
                "SELECT w.picker_name AS name, COUNT(*) AS waveCount, "
                        + "COALESCE(SUM(w.order_count),0) AS orderCount, "
                        + "COALESCE(SUM(w.total_qty),0) AS totalQty, "
                        + "ROUND(AVG(TIMESTAMPDIFF(MINUTE, w.start_time, w.pick_finish_time)),1) AS avgMinutes, "
                        + "COALESCE(SUM(TIMESTAMPDIFF(MINUTE, w.start_time, w.pick_finish_time)),0) AS totalMinutes "
                        + "FROM waves w "
                        + "WHERE w.picker_name IS NOT NULL AND w.start_time IS NOT NULL AND w.pick_finish_time IS NOT NULL "
                        + "GROUP BY w.picker_name ORDER BY totalQty DESC");
    }

    /** 复核员效率：复核订单数、通过数、异常数 */
    public List<Map<String, Object>> checkerEfficiency() {
        return jdbc.queryForList(
                "SELECT r.checker_name AS name, COUNT(*) AS reviewCount, "
                        + "SUM(CASE WHEN r.result = '通过' THEN 1 ELSE 0 END) AS passCount, "
                        + "SUM(CASE WHEN r.result = '异常' THEN 1 ELSE 0 END) AS errorCount "
                        + "FROM review_records r WHERE r.checker_name IS NOT NULL "
                        + "GROUP BY r.checker_name ORDER BY reviewCount DESC");
    }

    /** 缺货原因分布 */
    public List<Map<String, Object>> shortageByReason() {
        return jdbc.queryForList(
                "SELECT reason AS name, COUNT(*) AS value, COALESCE(SUM(short_qty),0) AS qty "
                        + "FROM shortages GROUP BY reason ORDER BY value DESC");
    }

    /** 整体汇总 */
    public Map<String, Object> overall() {
        return jdbc.queryForMap(
                "SELECT "
                        + "(SELECT COUNT(*) FROM waves) AS waveTotal, "
                        + "(SELECT COUNT(*) FROM waves WHERE status='已出库') AS waveDone, "
                        + "(SELECT COUNT(*) FROM orders) AS orderTotal, "
                        + "(SELECT COUNT(*) FROM orders WHERE status='已出库') AS orderDone, "
                        + "(SELECT COALESCE(SUM(total_qty),0) FROM outbounds) AS outboundQty, "
                        + "(SELECT COUNT(*) FROM review_records) AS reviewTotal, "
                        + "(SELECT COUNT(*) FROM error_records) AS errorTotal, "
                        + "(SELECT COUNT(*) FROM shortages) AS shortageTotal, "
                        + "(SELECT COALESCE(SUM(short_qty),0) FROM shortages) AS shortageQty");
    }

    /** 波次近 7 天产出趋势 */
    public List<Map<String, Object>> waveRecentDays() {
        return jdbc.queryForList(
                "SELECT DATE_FORMAT(create_time, '%m-%d') AS name, COUNT(*) AS value FROM waves "
                        + "WHERE create_time >= DATE_SUB(CURDATE(), INTERVAL 6 DAY) "
                        + "GROUP BY DATE_FORMAT(create_time, '%m-%d') ORDER BY MIN(create_time)");
    }

    /** 波次状态分布 */
    public List<Map<String, Object>> waveStatusDistribution() {
        return jdbc.queryForList("SELECT status AS name, COUNT(*) AS value FROM waves GROUP BY status ORDER BY value DESC");
    }
}
