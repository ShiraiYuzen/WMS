package com.example.wms.service;

import com.example.wms.common.Dict;
import com.example.wms.common.JdbcMaps;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.*;

/**
 * 看板与效率复盘聚合，计算口径与 A 前端 mock 完全一致。
 */
@Component
public class Stats {

    private final Repo repo;

    public Stats(Repo repo) {
        this.repo = repo;
    }

    private JdbcTemplate jdbc() {
        return repo.jdbc();
    }

    // ---------------- 通用 ----------------

    private static String range(String col, String start, String end, List<Object> args) {
        StringBuilder sb = new StringBuilder();
        if (start != null && !start.isBlank()) {
            sb.append(" AND ").append(col).append(" >= ?");
            args.add(start);
        }
        if (end != null && !end.isBlank()) {
            sb.append(" AND ").append(col).append(" <= ?");
            args.add(end.length() == 10 ? end + " 23:59:59" : end);
        }
        return sb.toString();
    }

    /** 有效日期范围：finish_time 优先，否则 create_time。 */
    private static String rangeEff(String start, String end, List<Object> args) {
        StringBuilder sb = new StringBuilder();
        if (start != null && !start.isBlank()) {
            sb.append(" AND LEFT(COALESCE(finish_time, create_time),10) >= ?");
            args.add(start);
        }
        if (end != null && !end.isBlank()) {
            sb.append(" AND LEFT(COALESCE(finish_time, create_time),10) <= ?");
            args.add(end);
        }
        return sb.toString();
    }

    private static String rangeReport(String start, String end, List<Object> args) {
        StringBuilder sb = new StringBuilder();
        if (start != null && !start.isBlank()) {
            sb.append(" AND LEFT(COALESCE(report_time, create_time),10) >= ?");
            args.add(start);
        }
        if (end != null && !end.isBlank()) {
            sb.append(" AND LEFT(COALESCE(report_time, create_time),10) <= ?");
            args.add(end);
        }
        return sb.toString();
    }

    private static double round(double v, int scale) {
        double p = Math.pow(10, scale);
        return Math.round(v * p) / p;
    }

    // ---------------- overview ----------------

    public Map<String, Object> overview(String start, String end) {
        // 任务：仅已完成/异常，按完成时间（或创建时间）落在区间
        List<Object> ta = new ArrayList<>();
        Map<String, Object> taskAgg = JdbcMaps.one(jdbc(),
                "SELECT COALESCE(SUM(actual_qty),0) picked, COALESCE(SUM(duration_sec),0) sec, " +
                        "COALESCE(SUM(should_qty),0) shouldQty, COALESCE(SUM(shortage_qty),0) shortage, COUNT(*) cnt " +
                        "FROM pick_task WHERE status IN ('FINISHED','EXCEPTION')" + rangeEff(start, end, ta),
                ta.toArray());
        double pickedQty = taskAgg == null ? 0 : Repo.d(taskAgg.get("picked"));
        double pickSeconds = taskAgg == null ? 0 : Repo.d(taskAgg.get("sec"));
        double shouldQty = taskAgg == null ? 0 : Repo.d(taskAgg.get("shouldQty"));
        double shortageQty = taskAgg == null ? 0 : Repo.d(taskAgg.get("shortage"));
        long taskCount = taskAgg == null ? 0 : Repo.i(taskAgg.get("cnt"));

        // 复核：按完成时间（或创建时间）落在区间
        List<Object> ra = new ArrayList<>();
        Map<String, Object> revAgg = JdbcMaps.one(jdbc(),
                "SELECT COUNT(*) total, SUM(CASE WHEN status='PASS' THEN 1 ELSE 0 END) passCnt, " +
                        "SUM(CASE WHEN status='DIFF' THEN 1 ELSE 0 END) diffCnt " +
                        "FROM review WHERE 1=1" + rangeEff(start, end, ra),
                ra.toArray());
        long reviewTotal = revAgg == null ? 0 : Repo.i(revAgg.get("total"));
        long passCount = revAgg == null ? 0 : Repo.i(revAgg.get("passCnt"));
        long diffCount = revAgg == null ? 0 : Repo.i(revAgg.get("diffCnt"));

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("totalOrders", JdbcMaps.count(jdbc(), "SELECT COUNT(*) FROM `order`"));
        r.put("pendingOrders", JdbcMaps.count(jdbc(), "SELECT COUNT(*) FROM `order` WHERE status='CREATED'"));
        r.put("pooledOrders", JdbcMaps.count(jdbc(), "SELECT COUNT(*) FROM `order` WHERE status='POOLED'"));
        r.put("totalWaves", JdbcMaps.count(jdbc(), "SELECT COUNT(*) FROM wave"));
        r.put("workingWaves", JdbcMaps.count(jdbc(),
                "SELECT COUNT(*) FROM wave WHERE status IN ('ASSIGNED','PICKING','REVIEWING')"));
        r.put("finishedWaves", JdbcMaps.count(jdbc(),
                "SELECT COUNT(*) FROM wave WHERE status IN ('HANDOVERED','CLOSED')"));
        r.put("pickedQty", Math.round(pickedQty));
        r.put("outboundOrders", JdbcMaps.count(jdbc(), "SELECT COUNT(*) FROM `order` WHERE status='OUTBOUND'"));
        r.put("handedPackages", JdbcMaps.count(jdbc(),
                "SELECT COALESCE(SUM(package_count),0) FROM handover WHERE status='CONFIRMED'"));
        double pickEfficiency = pickSeconds > 0 ? round(pickedQty * 3600d / pickSeconds, 2) : 0d;
        r.put("pickEfficiency", pickEfficiency);
        r.put("avgPickDurationSec", taskCount > 0 ? round(pickSeconds / taskCount, 2) : 0d);
        r.put("errorRate", reviewTotal > 0 ? round((double) diffCount / reviewTotal, 4) : 0d);
        r.put("shortageRate", shouldQty > 0 ? round(shortageQty / shouldQty, 4) : 0d);
        r.put("reviewPassRate", reviewTotal > 0 ? round((double) passCount / reviewTotal, 4) : 0d);
        r.put("pendingShortageCount", JdbcMaps.count(jdbc(), "SELECT COUNT(*) FROM shortage WHERE status='PENDING'"));
        r.put("alerts", alerts(pickEfficiency, Repo.d(r.get("errorRate")), Repo.d(r.get("shortageRate")),
                Repo.d(r.get("avgPickDurationSec"))));
        return r;
    }

    private List<Map<String, Object>> alerts(double pickEfficiency, double errorRate,
                                             double shortageRate, double avgDuration) {
        List<Map<String, Object>> list = new ArrayList<>();
        if (pickEfficiency > 0 && pickEfficiency < 120) {
            list.add(alert("pickEfficiency", "拣货效率", pickEfficiency, 120, "WARN",
                    "拣货效率 " + pickEfficiency + " 件/小时，低于目标 120 件/小时"));
        }
        if (errorRate > 0.005) {
            list.add(alert("errorRate", "差错率", errorRate, 0.005,
                    errorRate > 0.015 ? "DANGER" : "WARN",
                    "差错率 " + String.format("%.2f", errorRate * 100) + "%，高于目标 0.50%"));
        }
        if (shortageRate > 0.02) {
            list.add(alert("shortageRate", "缺货率", shortageRate, 0.02,
                    shortageRate > 0.06 ? "DANGER" : "WARN",
                    "缺货率 " + String.format("%.2f", shortageRate * 100) + "%，高于目标 2.00%"));
        }
        if (avgDuration > 3600) {
            list.add(alert("avgPickDuration", "平均拣货时长", avgDuration, 3600, "WARN",
                    "平均拣货时长 " + Math.round(avgDuration / 60) + " 分钟，高于目标 60 分钟"));
        }
        return list;
    }

    private static Map<String, Object> alert(String metric, String name, Object current, Object threshold,
                                             String level, String message) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("metric", metric);
        m.put("metricName", name);
        m.put("currentValue", current);
        m.put("threshold", threshold);
        m.put("level", level);
        m.put("message", message);
        return m;
    }

    // ---------------- trend ----------------

    /**
     * 趋势数据。
     *
     * <p>区间只有一天（看板的「今日」）时按**小时**出 24 个点，否则按天。
     * 一天一个点画出来只有一根柱子，看不出节奏。</p>
     */
    public List<Map<String, Object>> trend(String start, String end) {
        LocalDate endDate = (end != null && !end.isBlank()) ? LocalDate.parse(end) : LocalDate.now();
        LocalDate startDate = (start != null && !start.isBlank()) ? LocalDate.parse(start) : endDate.minusDays(6);
        if (startDate.isAfter(endDate)) startDate = endDate.minusDays(6);

        return startDate.equals(endDate) ? trendHourly(startDate) : trendDaily(startDate, endDate);
    }

    /** 按天：波次/订单按创建日，拣货产出与耗时按完成日 */
    private List<Map<String, Object>> trendDaily(LocalDate startDate, LocalDate endDate) {
        Map<String, Map<String, Object>> buckets = new TreeMap<>();
        for (LocalDate d = startDate; !d.isAfter(endDate); d = d.plusDays(1)) {
            buckets.put(d.toString(), emptyBucket(d.toString()));
        }

        for (Map<String, Object> w : JdbcMaps.list(jdbc(),
                "SELECT LEFT(create_time,10) k, COUNT(*) waveCount, COALESCE(SUM(order_count),0) orderCount, " +
                        "COALESCE(SUM(pick_duration_sec),0) dur " +
                        "FROM wave WHERE create_time IS NOT NULL GROUP BY LEFT(create_time,10)")) {
            Map<String, Object> b = buckets.get(Repo.str(w.get("k")));
            if (b == null) continue;
            b.put("waveCount", Repo.i(w.get("waveCount")));
            b.put("orderCount", Repo.i(w.get("orderCount")));
            b.put("_durSum", Repo.d(w.get("dur")));
        }

        // 拣货维度按「完成日」统计实际产出与耗时：拿 wave.total_qty 当件数是计划量，
        // 波次还没拣完也算进去，效率会被摊成 0。
        List<Object> ta = new ArrayList<>();
        for (Map<String, Object> t : JdbcMaps.list(jdbc(),
                "SELECT LEFT(COALESCE(finish_time, create_time),10) k, COALESCE(SUM(shortage_qty),0) shortage, " +
                        "COALESCE(SUM(actual_qty),0) picked, COALESCE(SUM(duration_sec),0) sec " +
                        "FROM pick_task WHERE status IN ('FINISHED','EXCEPTION')" +
                        rangeEff(startDate.toString(), endDate.toString(), ta) +
                        " GROUP BY LEFT(COALESCE(finish_time, create_time),10)", ta.toArray())) {
            Map<String, Object> b = buckets.get(Repo.str(t.get("k")));
            if (b != null) {
                b.put("shortageQty", Repo.i(t.get("shortage")));
                b.put("pickedQty", Repo.i(t.get("picked")));
                b.put("_pickSec", Repo.d(t.get("sec")));
            }
        }

        List<Object> ra = new ArrayList<>();
        for (Map<String, Object> rv : JdbcMaps.list(jdbc(),
                "SELECT LEFT(COALESCE(finish_time, create_time),10) k, COUNT(*) total, " +
                        "SUM(CASE WHEN status='DIFF' THEN 1 ELSE 0 END) diff " +
                        "FROM review WHERE status<>'WAITING'" +
                        rangeEff(startDate.toString(), endDate.toString(), ra) +
                        " GROUP BY LEFT(COALESCE(finish_time, create_time),10)", ra.toArray())) {
            Map<String, Object> b = buckets.get(Repo.str(rv.get("k")));
            if (b != null) {
                b.put("_reviewTotal", Repo.i(rv.get("total")));
                b.put("_diffTotal", Repo.i(rv.get("diff")));
            }
        }

        return finish(buckets);
    }

    /** 按小时：同一天内 00:00 ~ 23:00 共 24 个桶 */
    private List<Map<String, Object>> trendHourly(LocalDate day) {
        String d = day.toString();
        Map<String, Map<String, Object>> buckets = new TreeMap<>();
        for (int h = 0; h < 24; h++) {
            String hh = String.format("%02d", h);
            buckets.put(hh, emptyBucket(d + " " + hh + ":00"));
        }

        for (Map<String, Object> w : JdbcMaps.list(jdbc(),
                "SELECT substr(create_time,12,2) k, COUNT(*) waveCount, COALESCE(SUM(order_count),0) orderCount, " +
                        "COALESCE(SUM(pick_duration_sec),0) dur " +
                        "FROM wave WHERE create_time LIKE ? GROUP BY substr(create_time,12,2)", d + "%")) {
            Map<String, Object> b = buckets.get(Repo.str(w.get("k")));
            if (b == null) continue;
            b.put("waveCount", Repo.i(w.get("waveCount")));
            b.put("orderCount", Repo.i(w.get("orderCount")));
            b.put("_durSum", Repo.d(w.get("dur")));
        }

        for (Map<String, Object> t : JdbcMaps.list(jdbc(),
                "SELECT substr(COALESCE(finish_time, create_time),12,2) k, COALESCE(SUM(shortage_qty),0) shortage, " +
                        "COALESCE(SUM(actual_qty),0) picked, COALESCE(SUM(duration_sec),0) sec " +
                        "FROM pick_task WHERE status IN ('FINISHED','EXCEPTION') " +
                        "AND LEFT(COALESCE(finish_time, create_time),10)=? " +
                        "GROUP BY substr(COALESCE(finish_time, create_time),12,2)", d)) {
            Map<String, Object> b = buckets.get(Repo.str(t.get("k")));
            if (b != null) {
                b.put("shortageQty", Repo.i(t.get("shortage")));
                b.put("pickedQty", Repo.i(t.get("picked")));
                b.put("_pickSec", Repo.d(t.get("sec")));
            }
        }

        for (Map<String, Object> rv : JdbcMaps.list(jdbc(),
                "SELECT substr(COALESCE(finish_time, create_time),12,2) k, COUNT(*) total, " +
                        "SUM(CASE WHEN status='DIFF' THEN 1 ELSE 0 END) diff " +
                        "FROM review WHERE status<>'WAITING' AND LEFT(COALESCE(finish_time, create_time),10)=? " +
                        "GROUP BY substr(COALESCE(finish_time, create_time),12,2)", d)) {
            Map<String, Object> b = buckets.get(Repo.str(rv.get("k")));
            if (b != null) {
                b.put("_reviewTotal", Repo.i(rv.get("total")));
                b.put("_diffTotal", Repo.i(rv.get("diff")));
            }
        }

        return finish(buckets);
    }

    private static Map<String, Object> emptyBucket(String label) {
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("label", label);
        b.put("waveCount", 0);
        b.put("orderCount", 0);
        b.put("pickedQty", 0);
        b.put("pickEfficiency", null);
        b.put("errorRate", 0d);
        b.put("shortageQty", 0);
        b.put("avgDurationSec", 0d);
        b.put("_reviewTotal", 0);
        b.put("_diffTotal", 0);
        b.put("_durSum", 0d);
        b.put("_pickSec", 0d);
        return b;
    }

    /** 把累加用的临时字段换算成对外指标 */
    private static List<Map<String, Object>> finish(Map<String, Map<String, Object>> buckets) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> b : buckets.values()) {
            int reviewTotal = Repo.i(b.remove("_reviewTotal"));
            int diffTotal = Repo.i(b.remove("_diffTotal"));
            double durSum = Repo.d(b.remove("_durSum"));
            double pickSec = Repo.d(b.remove("_pickSec"));
            int waveCount = Repo.i(b.get("waveCount"));
            int picked = Repo.i(b.get("pickedQty"));
            b.put("errorRate", reviewTotal > 0 ? round((double) diffTotal / reviewTotal, 4) : 0d);
            b.put("avgDurationSec", waveCount > 0 ? round(durSum / waveCount, 2) : 0d);
            // 没有耗时记录就是「无数据」，给 null 让曲线断开；给 0 会被误读成「效率归零」
            b.put("pickEfficiency", pickSec > 0 ? round(picked * 3600d / pickSec, 2) : null);
            result.add(b);
        }
        return result;
    }


    // ---------------- 员工效率 ----------------

    public List<Map<String, Object>> employeeEfficiency(String start, String end, String employeeNo) {
        List<Map<String, Object>> employees = JdbcMaps.list(jdbc(),
                "SELECT * FROM employee WHERE role IN ('PICKER','REVIEWER') AND status=1 ORDER BY employee_no");

        Map<String, Map<String, Object>> taskAgg = new HashMap<>();
        List<Object> ta = new ArrayList<>();
        for (Map<String, Object> row : JdbcMaps.list(jdbc(),
                "SELECT picker_no, COUNT(*) cnt, COALESCE(SUM(actual_qty),0) qty, " +
                        "COALESCE(SUM(duration_sec),0) sec, COALESCE(SUM(location_count),0) locs " +
                        "FROM pick_task WHERE status IN ('FINISHED','EXCEPTION') AND picker_no IS NOT NULL" +
                        rangeEff(start, end, ta) + " GROUP BY picker_no", ta.toArray())) {
            taskAgg.put(Repo.str(row.get("pickerNo")), row);
        }

        Map<String, Map<String, Object>> revAgg = new HashMap<>();
        List<Object> ra = new ArrayList<>();
        for (Map<String, Object> row : JdbcMaps.list(jdbc(),
                "SELECT reviewer_no, COUNT(*) cnt, SUM(CASE WHEN status='DIFF' THEN 1 ELSE 0 END) errs, " +
                        "COALESCE(SUM(duration_sec),0) sec FROM review WHERE reviewer_no IS NOT NULL" +
                        rangeEff(start, end, ra) + " GROUP BY reviewer_no", ra.toArray())) {
            revAgg.put(Repo.str(row.get("reviewerNo")), row);
        }

        List<Map<String, Object>> list = new ArrayList<>();
        for (Map<String, Object> e : employees) {
            String no = Repo.str(e.get("employeeNo"));
            if (employeeNo != null && !employeeNo.isBlank() && !employeeNo.equals(no)) continue;
            String role = Repo.str(e.get("role"));

            Map<String, Object> vo = new LinkedHashMap<>();
            vo.put("employeeNo", no);
            vo.put("employeeName", e.get("employeeName"));
            vo.put("roleDesc", Dict.desc("employeeRole", role));
            vo.put("team", e.get("team"));
            vo.put("taskCount", 0);
            vo.put("pickedQty", 0);
            vo.put("qtyPerHour", 0d);
            vo.put("avgDurationSec", 0d);
            vo.put("locationCount", 0);
            vo.put("reviewCount", 0);
            vo.put("errorCount", 0);
            vo.put("errorRate", 0d);
            vo.put("orderPerHour", 0d);
            vo.put("score", 0d);

            if ("PICKER".equals(role)) {
                Map<String, Object> agg = taskAgg.get(no);
                if (agg != null) {
                    int count = Repo.i(agg.get("cnt"));
                    double qty = Repo.d(agg.get("qty"));
                    double sec = Repo.d(agg.get("sec"));
                    double qtyPerHour = sec > 0 ? round(qty * 3600d / sec, 2) : 0d;
                    vo.put("taskCount", count);
                    vo.put("pickedQty", Math.round(qty));
                    vo.put("qtyPerHour", qtyPerHour);
                    vo.put("avgDurationSec", count > 0 ? round(sec / count, 2) : 0d);
                    vo.put("locationCount", Repo.i(agg.get("locs")));
                    vo.put("score", round(Math.min(100d, qtyPerHour / 120d * 70d) + 30d, 2));
                }
            } else {
                Map<String, Object> agg = revAgg.get(no);
                if (agg != null) {
                    int count = Repo.i(agg.get("cnt"));
                    int errs = Repo.i(agg.get("errs"));
                    double sec = Repo.d(agg.get("sec"));
                    double errorRate = count > 0 ? round((double) errs / count, 4) : 0d;
                    vo.put("reviewCount", count);
                    vo.put("errorCount", errs);
                    vo.put("errorRate", errorRate);
                    vo.put("orderPerHour", sec > 0 ? round(count * 3600d / sec, 2) : 0d);
                    double acc = 1d - Math.min(1d, errorRate);
                    vo.put("score", round(Math.min(50d, count * 2.5d) + acc * 50d, 2));
                }
            }
            list.add(vo);
        }
        list.sort((a, b) -> Double.compare(Repo.d(b.get("score")), Repo.d(a.get("score"))));
        return list;
    }

    // ---------------- 差错分析 ----------------

    public Map<String, Object> errorRate(String start, String end) {
        List<Object> ra = new ArrayList<>();
        Map<String, Object> agg = JdbcMaps.one(jdbc(),
                "SELECT COUNT(*) total, SUM(CASE WHEN status='PASS' THEN 1 ELSE 0 END) passCnt, " +
                        "SUM(CASE WHEN status='DIFF' THEN 1 ELSE 0 END) diffCnt FROM review WHERE 1=1" +
                        rangeEff(start, end, ra), ra.toArray());
        long total = agg == null ? 0 : Repo.i(agg.get("total"));
        long pass = agg == null ? 0 : Repo.i(agg.get("passCnt"));
        long diff = agg == null ? 0 : Repo.i(agg.get("diffCnt"));

        List<Object> da = new ArrayList<>();
        Map<String, Object> diffAgg = JdbcMaps.one(jdbc(),
                "SELECT COALESCE(SUM(ABS(diff_qty)),0) qty FROM review_diff WHERE 1=1" +
                        range("create_time", start, end, da), da.toArray());
        long diffQtyTotal = diffAgg == null ? 0 : Math.round(Repo.d(diffAgg.get("qty")));

        List<Object> td = new ArrayList<>();
        List<Map<String, Object>> typeRows = JdbcMaps.list(jdbc(),
                "SELECT COALESCE(diff_type,'UNKNOWN') code, COUNT(*) cnt FROM review_diff WHERE 1=1" +
                        range("create_time", start, end, td) + " GROUP BY COALESCE(diff_type,'UNKNOWN') ORDER BY cnt DESC",
                td.toArray());
        List<Map<String, Object>> typeDistribution = distribution(typeRows, "diffType");

        List<Object> sd = new ArrayList<>();
        List<Map<String, Object>> stageRows = JdbcMaps.list(jdbc(),
                "SELECT COALESCE(responsible_stage,'UNKNOWN') code, COUNT(*) cnt FROM review_diff WHERE 1=1" +
                        range("create_time", start, end, sd) + " GROUP BY COALESCE(responsible_stage,'UNKNOWN') ORDER BY cnt DESC",
                sd.toArray());
        List<Map<String, Object>> stageDistribution = new ArrayList<>();
        long stageTotal = stageRows.stream().mapToLong(x -> Repo.i(x.get("cnt"))).sum();
        for (Map<String, Object> row : stageRows) {
            String code = Repo.str(row.get("code"));
            int count = Repo.i(row.get("cnt"));
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("code", code);
            m.put("name", stageName(code));
            m.put("count", count);
            m.put("ratio", stageTotal == 0 ? 0d : round((double) count / stageTotal, 4));
            stageDistribution.add(m);
        }

        List<Map<String, Object>> employees = employeeEfficiency(start, end, null);
        List<Map<String, Object>> topErrorEmployees = new ArrayList<>();
        for (Map<String, Object> e : employees) {
            if ("复核员".equals(Repo.str(e.get("roleDesc"))) && Repo.i(e.get("errorCount")) > 0) {
                topErrorEmployees.add(e);
            }
        }
        topErrorEmployees.sort((a, b) -> Double.compare(Repo.d(b.get("errorRate")), Repo.d(a.get("errorRate"))));
        if (topErrorEmployees.size() > 5) topErrorEmployees = new ArrayList<>(topErrorEmployees.subList(0, 5));

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("reviewTotal", total);
        r.put("passCount", pass);
        r.put("diffCount", diff);
        r.put("errorRate", total > 0 ? round((double) diff / total, 4) : 0d);
        r.put("diffQtyTotal", diffQtyTotal);
        r.put("typeDistribution", typeDistribution);
        r.put("stageDistribution", stageDistribution);
        r.put("topErrorEmployees", topErrorEmployees);
        return r;
    }

    private List<Map<String, Object>> distribution(List<Map<String, Object>> rows, String dictGroup) {
        long total = rows.stream().mapToLong(x -> Repo.i(x.get("cnt"))).sum();
        List<Map<String, Object>> list = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            String code = Repo.str(row.get("code"));
            int count = Repo.i(row.get("cnt"));
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("code", code);
            m.put("name", Dict.desc(dictGroup, code));
            m.put("count", count);
            m.put("ratio", total == 0 ? 0d : round((double) count / total, 4));
            list.add(m);
        }
        return list;
    }

    private static String stageName(String code) {
        if (code == null) return "待定";
        return switch (code) {
            case "PICK" -> "拣货环节";
            case "WAVE" -> "波次环节";
            case "DATA" -> "基础数据";
            default -> "待定";
        };
    }

    // ---------------- 缺货分布 ----------------

    public List<Map<String, Object>> shortageDistribution(String start, String end) {
        List<Object> a = new ArrayList<>();
        List<Map<String, Object>> rows = JdbcMaps.list(jdbc(),
                "SELECT reason code, COUNT(*) cnt FROM shortage WHERE 1=1" +
                        rangeReport(start, end, a) + " GROUP BY reason ORDER BY cnt DESC", a.toArray());
        long total = rows.stream().mapToLong(x -> Repo.i(x.get("cnt"))).sum();
        List<Map<String, Object>> list = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            String code = Repo.str(row.get("code"));
            int count = Repo.i(row.get("cnt"));
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("code", code);
            m.put("name", Dict.desc("shortageReason", code));
            m.put("count", count);
            m.put("ratio", total == 0 ? 0d : round((double) count / total, 4));
            list.add(m);
        }
        return list;
    }
}
