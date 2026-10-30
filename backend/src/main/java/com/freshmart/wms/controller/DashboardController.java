package com.freshmart.wms.controller;

import com.freshmart.wms.common.R;
import com.freshmart.wms.domain.enums.OrderStatus;
import com.freshmart.wms.domain.enums.WaveStatus;
import com.freshmart.wms.dto.AnalyticsQuery;
import com.freshmart.wms.repository.*;
import com.freshmart.wms.service.AnalyticsService;
import com.freshmart.wms.service.ShortageService;
import com.freshmart.wms.vo.OverviewVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 作业看板接口。
 *
 * <p>首页一次性拿到「核心 KPI + 各环节在制量 + 待办事项」，避免前端发多次请求。
 */
@Tag(name = "00. 作业看板", description = "首页总览数据")
@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final AnalyticsService analyticsService;
    private final ShortageService shortageService;
    private final OrderRepository orderRepo;
    private final WaveRepository waveRepo;
    private final PickTaskRepository taskRepo;
    private final ReviewRepository reviewRepo;

    @Operation(summary = "首页看板汇总")
    @GetMapping("/summary")
    public R<Map<String, Object>> summary() {
        AnalyticsQuery query = new AnalyticsQuery()
                .setStartDate(LocalDate.now().minusDays(6))
                .setEndDate(LocalDate.now());

        Map<String, Object> data = new LinkedHashMap<>();

        // 核心 KPI
        OverviewVO overview = analyticsService.overview(query);
        data.put("overview", overview);

        // 订单池水位：按状态给出各环节在制量
        List<Map<String, Object>> pipeline = new ArrayList<>();
        pipeline.add(stage("订单汇聚", orderRepo.count(o -> o.getStatus() == OrderStatus.CREATED),
                orderRepo.count(o -> o.getStatus() == OrderStatus.POOLED)));
        pipeline.add(stage("波次生成", waveRepo.count(w -> w.getStatus() == WaveStatus.CREATED),
                waveRepo.count(w -> w.getStatus() == WaveStatus.ASSIGNED)));
        pipeline.add(stage("拣货执行", taskRepo.count(t -> t.getStatus()
                == com.freshmart.wms.domain.enums.PickTaskStatus.WAITING),
                taskRepo.count(t -> t.getStatus() == com.freshmart.wms.domain.enums.PickTaskStatus.PICKING)));
        pipeline.add(stage("复核打包", reviewRepo.count(r -> r.getStatus()
                == com.freshmart.wms.domain.enums.ReviewStatus.WAITING),
                reviewRepo.count(r -> r.getStatus() == com.freshmart.wms.domain.enums.ReviewStatus.DIFF)));
        pipeline.add(stage("出库交接", waveRepo.count(w -> w.getStatus() == WaveStatus.REVIEWED),
                waveRepo.count(w -> w.getStatus() == WaveStatus.HANDOVERED)));
        data.put("pipeline", pipeline);

        // 待办
        List<Map<String, Object>> todos = new ArrayList<>();
        todos.add(todo("待汇聚订单", orderRepo.count(o -> o.getStatus() == OrderStatus.CREATED), "/order", "WAVE_OPERATOR"));
        todos.add(todo("待生成波次订单", orderRepo.count(o -> o.getStatus() == OrderStatus.POOLED), "/wave/generate", "WAVE_OPERATOR"));
        todos.add(todo("待处理缺货", shortageService.pendingCount(), "/shortage", "WAVE_OPERATOR"));
        todos.add(todo("待拣货任务", taskRepo.count(t -> t.getStatus()
                == com.freshmart.wms.domain.enums.PickTaskStatus.WAITING), "/pick", "PICKER"));
        todos.add(todo("待复核订单", reviewRepo.count(r -> r.getStatus()
                == com.freshmart.wms.domain.enums.ReviewStatus.WAITING), "/review", "REVIEWER"));
        todos.add(todo("待交接波次", waveRepo.count(w -> w.getStatus() == WaveStatus.REVIEWED), "/handover", "REVIEWER"));
        data.put("todos", todos);

        return R.ok(data);
    }

    @Operation(summary = "作业趋势（近 7 天）")
    @GetMapping("/trend")
    public R<?> trend() {
        AnalyticsQuery query = new AnalyticsQuery()
                .setStartDate(LocalDate.now().minusDays(6))
                .setEndDate(LocalDate.now())
                .setGranularity("DAY");
        return R.ok(analyticsService.trend(query));
    }

    private Map<String, Object> stage(String name, long pending, long working) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("stage", name);
        m.put("pending", pending);
        m.put("working", working);
        return m;
    }

    private Map<String, Object> todo(String name, long count, String path, String role) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", name);
        m.put("count", count);
        m.put("path", path);
        m.put("role", role);
        return m;
    }
}
