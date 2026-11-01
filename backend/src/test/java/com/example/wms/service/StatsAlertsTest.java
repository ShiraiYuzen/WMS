package com.example.wms.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 看板告警阈值单元测试。
 *
 * <p>看板的四条告警全是硬编码阈值，一旦被人改错（比如把 120 改成 1200、把 {@code >} 写成 {@code >=}），
 * 页面上不会报错，只会"该亮红灯的时候不亮"——这类缺陷靠手工点页面基本发现不了，只能靠边界值测试。</p>
 *
 * <p>阈值定义（与 {@code Stats#alerts} 一致）：</p>
 * <table border="1">
 *   <caption>告警规则</caption>
 *   <tr><th>指标</th><th>触发条件</th><th>等级</th></tr>
 *   <tr><td>拣货效率</td><td>{@code 0 < e < 120}</td><td>WARN</td></tr>
 *   <tr><td>差错率</td><td>{@code > 0.005}；{@code > 0.015} 升级</td><td>WARN / DANGER</td></tr>
 *   <tr><td>缺货率</td><td>{@code > 0.02}；{@code > 0.06} 升级</td><td>WARN / DANGER</td></tr>
 *   <tr><td>平均拣货时长</td><td>{@code > 3600} 秒</td><td>WARN</td></tr>
 * </table>
 *
 * <p><b>前置改动</b>：{@code Stats#alerts} 原本是 {@code private} 实例方法，但它不读取任何实例字段
 * （纯函数，只依赖入参），因此已改为 {@code static} 包级可见，测试可直接调用，不需要启动 Spring、
 * 也不需要连数据库。</p>
 */
@DisplayName("Stats — 看板告警阈值")
class StatsAlertsTest {

    /** 全部指标正常时不应产生任何告警 */
    private static List<Map<String, Object>> alerts(double eff, double error, double shortage, double duration) {
        return Stats.alerts(eff, error, shortage, duration);
    }

    @Test
    @DisplayName("全部指标达标 → 没有任何告警")
    void noAlertWhenAllHealthy() {
        assertThat(alerts(200, 0, 0, 600)).isEmpty();
    }

    // ------------------------------------------------------------------ 拣货效率

    @Nested
    @DisplayName("拣货效率（阈值 120 件/小时，且 > 0 才判断）")
    class PickEfficiency {

        @Test
        @DisplayName("119.99 件/小时 → WARN")
        void justBelowThreshold() {
            List<Map<String, Object>> result = alerts(119.99, 0, 0, 0);

            assertThat(result).hasSize(1);
            assertThat(result.get(0))
                    .containsEntry("metric", "pickEfficiency")
                    .containsEntry("level", "WARN")
                    .containsEntry("threshold", 120);
        }

        @Test
        @DisplayName("正好 120 件/小时 → 不告警（边界不含等于）")
        void exactlyAtThreshold() {
            assertThat(alerts(120, 0, 0, 0)).isEmpty();
        }

        @Test
        @DisplayName("效率为 0 → 不告警（0 表示区间内无数据，不是“效率为零”）")
        void zeroMeansNoData() {
            assertThat(alerts(0, 0, 0, 0)).isEmpty();
        }
    }

    // ------------------------------------------------------------------ 差错率

    @Nested
    @DisplayName("差错率（WARN > 0.50%，DANGER > 1.50%）")
    class ErrorRate {

        @ParameterizedTest(name = "差错率 {0} → {1}")
        @CsvSource({
                "0.005,   0",       // 边界：正好 0.50%，不告警
                "0.0051,  1",       // 刚超过 → WARN
                "0.015,   1",       // 边界：正好 1.50%，仍是 WARN
                "0.0151,  1",       // 刚超过 1.50% → DANGER
                "0.1,     1",
        })
        @DisplayName("各级别触发数量")
        void triggersAtExpectedLevel(double rate, int expectedCount) {
            assertThat(alerts(200, rate, 0, 0)).hasSize(expectedCount);
        }

        @Test
        @DisplayName("0.0051 → WARN，0.0151 → DANGER")
        void warnVersusDanger() {
            assertThat(alerts(200, 0.0051, 0, 0).get(0))
                    .containsEntry("metric", "errorRate")
                    .containsEntry("level", "WARN");

            assertThat(alerts(200, 0.0151, 0, 0).get(0))
                    .containsEntry("metric", "errorRate")
                    .containsEntry("level", "DANGER");
        }
    }

    // ------------------------------------------------------------------ 缺货率

    @Nested
    @DisplayName("缺货率（WARN > 2.00%，DANGER > 6.00%）")
    class ShortageRate {

        @ParameterizedTest(name = "缺货率 {0} → {1} 条")
        @CsvSource({
                "0.02,   0",        // 边界：正好 2.00%，不告警
                "0.0201, 1",        // 刚超过 → WARN
                "0.06,   1",        // 边界：正好 6.00%，仍是 WARN
                "0.0601, 1",        // 刚超过 6.00% → DANGER
        })
        @DisplayName("各级别触发数量")
        void triggersAtExpectedLevel(double rate, int expectedCount) {
            assertThat(alerts(200, 0, rate, 0)).hasSize(expectedCount);
        }

        @Test
        @DisplayName("0.0201 → WARN，0.0601 → DANGER")
        void warnVersusDanger() {
            assertThat(alerts(200, 0, 0.0201, 0).get(0))
                    .containsEntry("metric", "shortageRate")
                    .containsEntry("level", "WARN");

            assertThat(alerts(200, 0, 0.0601, 0).get(0))
                    .containsEntry("metric", "shortageRate")
                    .containsEntry("level", "DANGER");
        }
    }

    // ------------------------------------------------------------------ 平均拣货时长

    @Nested
    @DisplayName("平均拣货时长（阈值 3600 秒）")
    class AverageDuration {

        @Test
        @DisplayName("正好 3600 秒（60 分钟）→ 不告警")
        void exactlyOneHour() {
            assertThat(alerts(200, 0, 0, 3600)).isEmpty();
        }

        @Test
        @DisplayName("3600.1 秒 → WARN")
        void justOverOneHour() {
            List<Map<String, Object>> result = alerts(200, 0, 0, 3600.1);

            assertThat(result).hasSize(1);
            assertThat(result.get(0))
                    .containsEntry("metric", "avgPickDuration")
                    .containsEntry("level", "WARN");
        }
    }

    // ------------------------------------------------------------------ 组合与结构

    @Test
    @DisplayName("四项全部超标 → 一次返回四条，且顺序固定")
    void allFourAlerts() {
        List<Map<String, Object>> result = alerts(50, 0.05, 0.1, 7200);

        assertThat(result).hasSize(4);
        assertThat(result)
                .extracting(m -> m.get("metric"))
                .containsExactly("pickEfficiency", "errorRate", "shortageRate", "avgPickDuration");
    }

    @Test
    @DisplayName("每条告警都带齐六个字段，前端可直接渲染")
    void alertShapeIsComplete() {
        Map<String, Object> alert = alerts(50, 0, 0, 0).get(0);

        assertThat(alert).containsKeys(
                "metric", "metricName", "currentValue", "threshold", "level", "message");
        assertThat(alert.get("message")).asString().isNotBlank();
    }

    @Test
    @DisplayName("等级只有 WARN 与 DANGER 两种取值")
    void levelIsAlwaysWarnOrDanger() {
        List<Map<String, Object>> result = alerts(50, 0.05, 0.1, 7200);

        assertThat(result)
                .extracting(m -> m.get("level"))
                .allMatch(l -> "WARN".equals(l) || "DANGER".equals(l));
    }
}
