package com.freshmart.wms.service;

import com.freshmart.wms.domain.entity.*;
import com.freshmart.wms.domain.enums.*;
import com.freshmart.wms.dto.AnalyticsQuery;
import com.freshmart.wms.repository.*;
import com.freshmart.wms.vo.EmployeeEfficiencyVO;
import com.freshmart.wms.vo.ErrorRateVO;
import com.freshmart.wms.vo.OverviewVO;
import com.freshmart.wms.vo.TrendPointVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.WeekFields;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 效率复盘服务 —— 闭环第六环「效率复盘」。
 *
 * <h3>指标口径</h3>
 * <table border="1">
 *   <tr><th>指标</th><th>公式</th><th>说明</th></tr>
 *   <tr><td>拣货效率</td><td>实拣件数 ÷ 拣货总工时</td><td>单位 件/小时，反映拣货员的作业速度</td></tr>
 *   <tr><td>差错率</td><td>有差异的复核单数 ÷ 复核单总数</td><td>反映拣货与复核环节的准确度</td></tr>
 *   <tr><td>缺货率</td><td>缺货件数 ÷ 应拣件数</td><td>反映库存准确度与备货水平</td></tr>
 *   <tr><td>复核通过率</td><td>复核通过单数 ÷ 复核单总数</td><td>差错率的反向指标</td></tr>
 *   <tr><td>平均波次时长</td><td>Σ波次拣货耗时 ÷ 波次数</td><td>反映波次规模与作业流畅度</td></tr>
 * </table>
 *
 * <p>阈值取自 {@code application.yml} 的 {@code wms.metric.*}，
 * 超出阈值会在总览接口的 {@code alerts} 中返回，前端用红色标记提示。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private final OrderRepository orderRepo;
    private final WaveRepository waveRepo;
    private final WaveOrderRepository waveOrderRepo;
    private final PickTaskRepository taskRepo;
    private final PickItemRepository pickItemRepo;
    private final ShortageRepository shortageRepo;
    private final ReviewRepository reviewRepo;
    private final ReviewDiffRepository diffRepo;
    private final EmployeeRepository employeeRepo;
    private final HandoverRepository handoverRepo;

    @Value("${wms.metric.pick-efficiency-warn:120}")
    private double pickEfficiencyWarn;

    @Value("${wms.metric.error-rate-warn:0.005}")
    private double errorRateWarn;

    @Value("${wms.metric.shortage-rate-warn:0.02}")
    private double shortageRateWarn;

    @Value("${wms.metric.wave-duration-warn:3600}")
    private double waveDurationWarn;

    /* ====================================================================== */
    /* 总览                                                                    */
    /* ====================================================================== */

    public OverviewVO overview(AnalyticsQuery query) {
        OverviewVO vo = new OverviewVO();

        vo.setTotalOrders(orderRepo.count());
        vo.setPendingOrders(orderRepo.count(o -> o.getStatus() == OrderStatus.CREATED));
        vo.setPooledOrders(orderRepo.count(o -> o.getStatus() == OrderStatus.POOLED));
        vo.setTotalWaves(waveRepo.count());
        vo.setWorkingWaves(waveRepo.count(w -> w.getStatus() != null && w.getStatus().isWorking()));
        vo.setFinishedWaves(waveRepo.count(w -> w.getStatus() == WaveStatus.CLOSED
                || w.getStatus() == WaveStatus.HANDOVERED));
        vo.setOutboundOrders(orderRepo.count(o -> o.getStatus() == OrderStatus.OUTBOUND));
        vo.setHandedPackages(handoverRepo.findAll().stream()
                .filter(h -> h.getStatus() == HandoverStatus.CONFIRMED)
                .mapToLong(h -> h.getPackageCount() == null ? 0 : h.getPackageCount()).sum());

        List<PickTask> tasks = inRangeTasks(query);
        long pickedQty = tasks.stream().mapToLong(t -> t.getActualQty() == null ? 0 : t.getActualQty()).sum();
        long pickSeconds = tasks.stream().mapToLong(t -> t.getDurationSec() == null ? 0 : t.getDurationSec()).sum();
        vo.setPickedQty(pickedQty);
        vo.setPickEfficiency(round2(pickSeconds <= 0 ? 0 : pickedQty * 3600.0 / pickSeconds));
        vo.setAvgPickDurationSec(round2(tasks.isEmpty() ? 0 : pickSeconds * 1.0 / tasks.size()));

        List<ReviewRecord> reviews = inRangeReviews(query);
        long reviewTotal = reviews.size();
        long passCount = reviews.stream().filter(r -> r.getStatus() == ReviewStatus.PASS).count();
        long diffCount = reviews.stream().filter(r -> r.getStatus() == ReviewStatus.DIFF).count();
        vo.setReviewPassRate(round4(reviewTotal == 0 ? 0 : passCount * 1.0 / reviewTotal));
        vo.setErrorRate(round4(reviewTotal == 0 ? 0 : diffCount * 1.0 / reviewTotal));

        long shouldQty = tasks.stream().mapToLong(t -> t.getShouldQty() == null ? 0 : t.getShouldQty()).sum();
        long shortageQty = tasks.stream().mapToLong(t -> t.getShortageQty() == null ? 0 : t.getShortageQty()).sum();
        vo.setShortageRate(round4(shouldQty == 0 ? 0 : shortageQty * 1.0 / shouldQty));
        vo.setPendingShortageCount(shortageRepo.count(s -> s.getStatus() == ShortageStatus.PENDING));

        // 指标预警
        List<OverviewVO.MetricAlert> alerts = new ArrayList<>();
        if (vo.getPickEfficiency() > 0 && vo.getPickEfficiency() < pickEfficiencyWarn) {
            alerts.add(new OverviewVO.MetricAlert().setMetric("pickEfficiency").setMetricName("拣货效率")
                    .setCurrentValue(vo.getPickEfficiency()).setThreshold(pickEfficiencyWarn).setLevel("WARN")
                    .setMessage("拣货效率 " + vo.getPickEfficiency() + " 件/小时，低于目标 "
                            + (int) pickEfficiencyWarn + " 件/小时"));
        }
        if (vo.getErrorRate() > errorRateWarn) {
            alerts.add(new OverviewVO.MetricAlert().setMetric("errorRate").setMetricName("差错率")
                    .setCurrentValue(vo.getErrorRate()).setThreshold(errorRateWarn)
                    .setLevel(vo.getErrorRate() > errorRateWarn * 3 ? "DANGER" : "WARN")
                    .setMessage("差错率 " + pct(vo.getErrorRate()) + "，高于目标 " + pct(errorRateWarn)));
        }
        if (vo.getShortageRate() > shortageRateWarn) {
            alerts.add(new OverviewVO.MetricAlert().setMetric("shortageRate").setMetricName("缺货率")
                    .setCurrentValue(vo.getShortageRate()).setThreshold(shortageRateWarn)
                    .setLevel(vo.getShortageRate() > shortageRateWarn * 3 ? "DANGER" : "WARN")
                    .setMessage("缺货率 " + pct(vo.getShortageRate()) + "，高于目标 " + pct(shortageRateWarn)));
        }
        if (vo.getAvgPickDurationSec() > waveDurationWarn) {
            alerts.add(new OverviewVO.MetricAlert().setMetric("avgPickDuration").setMetricName("平均拣货时长")
                    .setCurrentValue(vo.getAvgPickDurationSec()).setThreshold(waveDurationWarn).setLevel("WARN")
                    .setMessage("平均拣货时长 " + Math.round(vo.getAvgPickDurationSec() / 60.0)
                            + " 分钟，高于目标 " + Math.round(waveDurationWarn / 60.0) + " 分钟"));
        }
        vo.setAlerts(alerts);
        return vo;
    }

    /* ====================================================================== */
    /* 人员效率                                                                */
    /* ====================================================================== */

    public List<EmployeeEfficiencyVO> employeeEfficiency(AnalyticsQuery query) {
        List<EmployeeEfficiencyVO> list = new ArrayList<>();
        List<PickTask> tasks = inRangeTasks(query);
        List<ReviewRecord> reviews = inRangeReviews(query);

        for (Employee e : employeeRepo.findAll()) {
            if (e.getRole() != EmployeeRole.PICKER && e.getRole() != EmployeeRole.REVIEWER) {
                continue;
            }
            if (!blank(query.getEmployeeNo()) && !query.getEmployeeNo().equals(e.getEmployeeNo())) {
                continue;
            }

            EmployeeEfficiencyVO vo = new EmployeeEfficiencyVO()
                    .setEmployeeNo(e.getEmployeeNo())
                    .setEmployeeName(e.getEmployeeName())
                    .setRoleDesc(e.getRole().getDesc())
                    .setTeam(e.getTeam());

            if (e.getRole() == EmployeeRole.PICKER) {
                List<PickTask> mine = tasks.stream()
                        .filter(t -> e.getEmployeeNo().equals(t.getPickerNo())).collect(Collectors.toList());
                long qty = mine.stream().mapToLong(t -> t.getActualQty() == null ? 0 : t.getActualQty()).sum();
                long sec = mine.stream().mapToLong(t -> t.getDurationSec() == null ? 0 : t.getDurationSec()).sum();
                long locs = mine.stream().mapToLong(t -> t.getLocationCount() == null ? 0 : t.getLocationCount()).sum();
                vo.setTaskCount((long) mine.size())
                        .setPickedQty(qty)
                        .setQtyPerHour(round2(sec <= 0 ? 0 : qty * 3600.0 / sec))
                        .setAvgDurationSec(round2(mine.isEmpty() ? 0 : sec * 1.0 / mine.size()))
                        .setLocationCount(locs);
                // 拣货员评分：效率占 70%，准确度占 30%（以其经手波次的差错情况衡量）
                double effScore = Math.min(100, vo.getQtyPerHour() / Math.max(1, pickEfficiencyWarn) * 70);
                vo.setScore(round2(effScore + 30));
            } else {
                List<ReviewRecord> mine = reviews.stream()
                        .filter(r -> e.getEmployeeNo().equals(r.getReviewerNo())).collect(Collectors.toList());
                long errs = mine.stream().filter(r -> r.getStatus() == ReviewStatus.DIFF).count();
                long sec = mine.stream().mapToLong(r -> r.getDurationSec() == null ? 0 : r.getDurationSec()).sum();
                vo.setReviewCount((long) mine.size())
                        .setErrorCount(errs)
                        .setErrorRate(round4(mine.isEmpty() ? 0 : errs * 1.0 / mine.size()))
                        .setOrderPerHour(round2(sec <= 0 ? 0 : mine.size() * 3600.0 / sec));
                // 复核员评分：工作量占 50%，准确度（1-差错率）占 50%
                double acc = 1 - Math.min(1, vo.getErrorRate());
                vo.setScore(round2(Math.min(50, vo.getReviewCount() * 2.5) + acc * 50));
            }
            list.add(vo);
        }

        list.sort(Comparator.comparingDouble(EmployeeEfficiencyVO::getScore).reversed());
        return list;
    }

    /* ====================================================================== */
    /* 差错率统计                                                               */
    /* ====================================================================== */

    public ErrorRateVO errorRate(AnalyticsQuery query) {
        List<ReviewRecord> reviews = inRangeReviews(query);
        long total = reviews.size();
        long pass = reviews.stream().filter(r -> r.getStatus() == ReviewStatus.PASS).count();
        long diff = reviews.stream().filter(r -> r.getStatus() == ReviewStatus.DIFF).count();

        List<ReviewDiffItem> diffs = diffRepo.findAll().stream()
                .filter(d -> inRange(d.getCreateTime(), query))
                .collect(Collectors.toList());

        ErrorRateVO vo = new ErrorRateVO()
                .setReviewTotal(total)
                .setPassCount(pass)
                .setDiffCount(diff)
                .setErrorRate(round4(total == 0 ? 0 : diff * 1.0 / total))
                .setDiffQtyTotal(diffs.stream()
                        .mapToLong(d -> d.getDiffQty() == null ? 0 : Math.abs(d.getDiffQty())).sum());

        // 按差异类型分布
        vo.setTypeDistribution(distribute(diffs.stream()
                .map(d -> d.getDiffType() == null ? "UNKNOWN" : d.getDiffType().getCode())
                .collect(Collectors.toList()), code -> {
            try {
                return DiffType.of(code).getDesc();
            } catch (Exception ex) {
                return "未知";
            }
        }));

        // 按责任环节分布
        vo.setStageDistribution(distribute(diffs.stream()
                .map(d -> d.getResponsibleStage() == null ? "UNKNOWN" : d.getResponsibleStage())
                .collect(Collectors.toList()), code -> switch (code) {
            case "PICK" -> "拣货环节";
            case "WAVE" -> "波次环节";
            case "DATA" -> "基础数据";
            default -> "待定";
        }));

        // 差错率最高的复核员
        vo.setTopErrorEmployees(employeeEfficiency(query).stream()
                .filter(e -> "复核员".equals(e.getRoleDesc()))
                .filter(e -> e.getErrorCount() != null && e.getErrorCount() > 0)
                .sorted(Comparator.comparingDouble(EmployeeEfficiencyVO::getErrorRate).reversed())
                .limit(5)
                .collect(Collectors.toList()));
        return vo;
    }

    /* ====================================================================== */
    /* 趋势                                                                    */
    /* ====================================================================== */

    public List<TrendPointVO> trend(AnalyticsQuery query) {
        LocalDate end = query.getEndDate() == null ? LocalDate.now() : query.getEndDate();
        LocalDate start = query.getStartDate() == null ? end.minusDays(6) : query.getStartDate();
        boolean byWeek = "WEEK".equalsIgnoreCase(query.getGranularity());

        // 1) 先按时间轴铺满桶，保证没有数据的日期也出现在图上（值为 0）
        Map<String, TrendPointVO> buckets = new LinkedHashMap<>();
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            buckets.computeIfAbsent(labelOf(d, byWeek), k -> new TrendPointVO().setLabel(k));
        }

        // 比率类指标需要分子/分母分开累加，最后统一相除，避免中途取整丢精度
        Map<String, long[]> reviewStat = new HashMap<>();   // [复核单数, 差异单数]
        Map<String, Long> durationSum = new HashMap<>();    // 波次拣货耗时合计

        // 2) 波次维度：波次数、订单数、拣货件数、耗时合计
        for (Wave w : waveRepo.findAll()) {
            if (w.getCreateTime() == null || !inRange(w.getCreateTime(), query)) {
                continue;
            }
            TrendPointVO p = buckets.get(labelOf(w.getCreateTime().toLocalDate(), byWeek));
            if (p == null) {
                continue;
            }
            p.setWaveCount(p.getWaveCount() + 1);
            p.setOrderCount(p.getOrderCount() + (w.getOrderCount() == null ? 0 : w.getOrderCount()));
            p.setPickedQty(p.getPickedQty() + (w.getTotalQty() == null ? 0 : w.getTotalQty()));
            if (w.getPickDurationSec() != null) {
                durationSum.merge(p.getLabel(), w.getPickDurationSec(), Long::sum);
            }
        }

        // 3) 拣货任务维度：缺货件数
        for (PickTask t : inRangeTasks(query)) {
            LocalDateTime time = t.getFinishTime() != null ? t.getFinishTime() : t.getCreateTime();
            if (time == null) {
                continue;
            }
            TrendPointVO p = buckets.get(labelOf(time.toLocalDate(), byWeek));
            if (p != null) {
                p.setShortageQty(p.getShortageQty() + (t.getShortageQty() == null ? 0 : t.getShortageQty()));
            }
        }

        // 4) 复核维度：复核单数（分母）与差异单数（分子）
        for (ReviewRecord r : inRangeReviews(query)) {
            if (r.getStatus() == ReviewStatus.WAITING) {
                continue;
            }
            LocalDateTime time = r.getFinishTime() != null ? r.getFinishTime() : r.getCreateTime();
            if (time == null) {
                continue;
            }
            TrendPointVO p = buckets.get(labelOf(time.toLocalDate(), byWeek));
            if (p == null) {
                continue;
            }
            long[] stat = reviewStat.computeIfAbsent(p.getLabel(), k -> new long[2]);
            stat[0]++;
            if (r.getStatus() == ReviewStatus.DIFF) {
                stat[1]++;
            }
        }

        // 5) 收尾：把累加值换算成比率与均值
        List<TrendPointVO> result = new ArrayList<>(buckets.values());
        for (TrendPointVO p : result) {
            long[] stat = reviewStat.getOrDefault(p.getLabel(), new long[2]);
            p.setErrorRate(round4(stat[0] == 0 ? 0 : stat[1] * 1.0 / stat[0]));

            long durSum = durationSum.getOrDefault(p.getLabel(), 0L);
            p.setAvgDurationSec(round2(p.getWaveCount() == 0 ? 0 : durSum * 1.0 / p.getWaveCount()));
            // 拣货效率 = 当日拣货件数 ÷ 当日拣货总工时
            p.setPickEfficiency(round2(durSum <= 0 ? 0 : p.getPickedQty() * 3600.0 / durSum));
        }
        return result;
    }

    /* ====================================================================== */
    /* 缺货分析                                                                */
    /* ====================================================================== */

    public List<ErrorRateVO.TypeDistribution> shortageDistribution(AnalyticsQuery query) {
        List<ShortageRecord> records = shortageRepo.findAll().stream()
                .filter(s -> inRange(s.getReportTime() != null ? s.getReportTime() : s.getCreateTime(), query))
                .collect(Collectors.toList());
        return distribute(records.stream()
                        .map(s -> s.getReason() == null ? "UNKNOWN" : s.getReason().getCode())
                        .collect(Collectors.toList()),
                code -> ShortageReason.of(code).getDesc());
    }

    /* ====================================================================== */
    /* 内部工具                                                                */
    /* ====================================================================== */

    private List<PickTask> inRangeTasks(AnalyticsQuery query) {
        return taskRepo.findAll().stream()
                .filter(t -> inRange(t.getFinishTime() != null ? t.getFinishTime() : t.getCreateTime(), query))
                .filter(t -> t.getStatus() == PickTaskStatus.FINISHED || t.getStatus() == PickTaskStatus.EXCEPTION)
                .collect(Collectors.toList());
    }

    private List<ReviewRecord> inRangeReviews(AnalyticsQuery query) {
        return reviewRepo.findAll().stream()
                .filter(r -> inRange(r.getFinishTime() != null ? r.getFinishTime() : r.getCreateTime(), query))
                .collect(Collectors.toList());
    }

    private boolean inRange(LocalDateTime time, AnalyticsQuery query) {
        if (time == null) {
            return false;
        }
        LocalDate d = time.toLocalDate();
        if (query.getStartDate() != null && d.isBefore(query.getStartDate())) {
            return false;
        }
        return query.getEndDate() == null || !d.isAfter(query.getEndDate());
    }

    private List<ErrorRateVO.TypeDistribution> distribute(List<String> codes,
                                                          java.util.function.Function<String, String> namer) {
        Map<String, Long> counts = codes.stream()
                .collect(Collectors.groupingBy(c -> c, LinkedHashMap::new, Collectors.counting()));
        long total = codes.size();
        List<ErrorRateVO.TypeDistribution> list = new ArrayList<>();
        counts.forEach((code, count) -> list.add(new ErrorRateVO.TypeDistribution()
                .setCode(code).setName(namer.apply(code)).setCount(count)
                .setRatio(round4(total == 0 ? 0 : count * 1.0 / total))));
        list.sort(Comparator.comparingLong(ErrorRateVO.TypeDistribution::getCount).reversed());
        return list;
    }

    private static String weekLabel(LocalDate d) {
        WeekFields wf = WeekFields.ISO;
        return d.getYear() + "-W" + String.format("%02d", d.get(wf.weekOfWeekBasedYear()));
    }

    private static String labelOf(LocalDate d, boolean byWeek) {
        return byWeek ? weekLabel(d) : d.format(DateTimeFormatter.ISO_DATE);
    }

    private static double round2(double v) {
        return Math.round(v * 100) / 100.0;
    }

    private static double round4(double v) {
        return Math.round(v * 10000) / 10000.0;
    }

    private static String pct(double v) {
        return String.format("%.2f%%", v * 100);
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
