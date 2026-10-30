package com.freshmart.wms.common;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 业务单号生成器。
 *
 * <p>格式：{@code 前缀 + yyyyMMdd + 4 位当日流水}，例如 {@code SO202610160001}。
 *
 * <p><b>接库后的替换点</b>：当前用内存计数器发号，重启会归零。
 * 接入数据库后应改为「单号规则表 + 悲观锁」或「数据库序列 + 乐观重试」，
 * 保证多实例部署下单号不重复。替换时只需改本类实现，调用方无需改动。
 */
@Component
public class BizNoGenerator {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final Map<String, AtomicInteger> counters = new ConcurrentHashMap<>();

    /**
     * 生成单号。
     *
     * @param prefix 业务前缀：SO 订单 / WV 波次 / PK 拣货 / SH 缺货 / RV 复核 / HO 交接
     */
    public String next(String prefix) {
        String key = prefix + LocalDate.now().format(DAY);
        int seq = counters.computeIfAbsent(key, k -> new AtomicInteger(0)).incrementAndGet();
        return key + String.format("%04d", seq);
    }

    /** 按指定日期生成（用于补录历史单据）。 */
    public String next(String prefix, LocalDate date) {
        String key = prefix + date.format(DAY);
        int seq = counters.computeIfAbsent(key, k -> new AtomicInteger(0)).incrementAndGet();
        return key + String.format("%04d", seq);
    }

    /** 让发号器跳过已存在的序号（种子数据初始化后调用，避免与历史单号撞号）。 */
    public void syncFloor(String prefix, int floor) {
        String key = prefix + LocalDate.now().format(DAY);
        counters.computeIfAbsent(key, k -> new AtomicInteger(0))
                .accumulateAndGet(floor, Math::max);
    }
}
