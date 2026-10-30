package com.freshmart.wms.service;

import com.freshmart.wms.common.BizException;
import com.freshmart.wms.common.BizNoGenerator;
import com.freshmart.wms.common.ErrorCode;
import com.freshmart.wms.common.PageResult;
import com.freshmart.wms.domain.entity.*;
import com.freshmart.wms.domain.enums.*;
import com.freshmart.wms.dto.WaveAssignRequest;
import com.freshmart.wms.dto.WaveGenerateRequest;
import com.freshmart.wms.dto.WaveQuery;
import com.freshmart.wms.repository.*;
import com.freshmart.wms.vo.WavePreviewVO;
import com.freshmart.wms.vo.WaveVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 波次服务 —— 闭环第二环「波次生成」，同时承担波次全生命周期的状态推进。
 *
 * <h3>生成算法</h3>
 * <ol>
 *   <li><b>取候选订单</b>：指定订单则以指定为准；否则取全部「已汇聚入池」订单。</li>
 *   <li><b>策略聚类</b>：按策略把候选订单分成若干组（区域 / 承运商 / 时效 / 单量）。</li>
 *   <li><b>容量切分</b>：组内再按「最大订单数 / 最大 SKU 数 / 最大件数」上限切分成若干波次。</li>
 *   <li><b>合并拣货明细</b>：波次内所有订单的明细按「库位 + SKU」合并，
 *       并按库位路径排序，得到拣货员的最优行走路线。</li>
 * </ol>
 *
 * <h3>状态推进</h3>
 * 波次状态由本服务与 {@link PickService}、{@link ReviewService}、{@link HandoverService} 协同推进，
 * 每次推进都会回写时间戳，作为效率复盘的数据源。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WaveService {

    private final WaveRepository waveRepo;
    private final WaveOrderRepository waveOrderRepo;
    private final OrderRepository orderRepo;
    private final OrderItemRepository orderItemRepo;
    private final PickTaskRepository pickTaskRepo;
    private final PickItemRepository pickItemRepo;
    private final LocationRepository locationRepo;
    private final EmployeeRepository employeeRepo;
    private final OperationLogRepository logRepo;
    private final BizNoGenerator noGen;

    /* ====================================================================== */
    /* 查询                                                                    */
    /* ====================================================================== */

    public PageResult<WaveVO> pageWaves(WaveQuery query) {
        List<WaveVO> all = waveRepo.findAll().stream()
                .filter(w -> blank(query.getWaveNo())
                        || w.getWaveNo().toLowerCase().contains(query.getWaveNo().toLowerCase())
                        || (w.getWaveName() != null && w.getWaveName().contains(query.getWaveNo())))
                .filter(w -> blank(query.getStatus())
                        || (w.getStatus() != null && query.getStatus().equals(w.getStatus().getCode())))
                .filter(w -> blank(query.getStrategy())
                        || (w.getStrategy() != null && query.getStrategy().equals(w.getStrategy().getCode())))
                .filter(w -> blank(query.getPickerNo()) || query.getPickerNo().equals(w.getPickerNo()))
                .filter(w -> !Boolean.TRUE.equals(query.getOnlyWorking())
                        || (w.getStatus() != null && w.getStatus().isWorking()))
                .filter(w -> !Boolean.TRUE.equals(query.getOnlyShortage())
                        || Boolean.TRUE.equals(w.getHasShortage()))
                .map(this::toVO)
                .collect(Collectors.toList());

        // 在制波次排前，其余按生成时间倒序
        all.sort(Comparator.comparing((WaveVO v) -> v.getStatus() == null || !v.getStatus().isWorking())
                .thenComparing(v -> v.getCreateTime() == null ? LocalDateTime.MIN : v.getCreateTime(),
                        Comparator.reverseOrder()));
        return PageResult.of(all, query);
    }

    public WaveVO getWave(String waveNo) {
        Wave wave = findWave(waveNo);
        return toVO(wave);
    }

    /** 拣货员负载看板：每个在岗拣货员当前手上有几个未完成波次。 */
    public List<Map<String, Object>> pickerWorkload() {
        List<Employee> pickers = employeeRepo.find(e -> e.getRole() == EmployeeRole.PICKER
                && Integer.valueOf(1).equals(e.getStatus()));
        List<Map<String, Object>> result = new ArrayList<>();
        for (Employee p : pickers) {
            long waiting = waveRepo.count(w -> p.getEmployeeNo().equals(w.getPickerNo())
                    && w.getStatus() == WaveStatus.ASSIGNED);
            long working = waveRepo.count(w -> p.getEmployeeNo().equals(w.getPickerNo())
                    && w.getStatus() != null && w.getStatus().isWorking());
            long done = waveRepo.count(w -> p.getEmployeeNo().equals(w.getPickerNo())
                    && (w.getStatus() == WaveStatus.HANDOVERED || w.getStatus() == WaveStatus.CLOSED));
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("pickerNo", p.getEmployeeNo());
            m.put("pickerName", p.getEmployeeName());
            m.put("team", p.getTeam());
            m.put("waitingWaves", waiting);
            m.put("workingWaves", working);
            m.put("finishedWaves", done);
            m.put("load", waiting + working);
            result.add(m);
        }
        result.sort(Comparator.comparingLong(m -> (Long) m.get("load")));
        return result;
    }

    /* ====================================================================== */
    /* 波次生成：预览                                                           */
    /* ====================================================================== */

    /**
     * 预览波次生成结果（不落库）。
     * 波次员先看清楚会被切成几波、每波多少单，确认后再正式生成。
     */
    public WavePreviewVO preview(WaveGenerateRequest req) {
        List<OrderInfo> candidates = resolveCandidates(req);
        BizException.assertTrue(!candidates.isEmpty(), ErrorCode.WAVE_NO_CANDIDATE_ORDER);

        List<WavePreviewVO.GroupPreview> groups = buildGroups(candidates, req);

        int totalQty = groups.stream().mapToInt(WavePreviewVO.GroupPreview::getTotalQty).sum();
        return new WavePreviewVO()
                .setStrategy(req.getStrategy())
                .setStrategyDesc(WaveStrategy.of(req.getStrategy()).getDesc())
                .setWaveCount(groups.size())
                .setTotalOrders(candidates.size())
                .setTotalQty(totalQty)
                .setGroups(groups);
    }

    /* ====================================================================== */
    /* 波次生成：落库                                                           */
    /* ====================================================================== */

    /**
     * 正式生成波次。会把参与订单置为 {@code WAVED}，并同步生成拣货任务与明细。
     */
    public List<WaveVO> generate(WaveGenerateRequest req) {
        List<OrderInfo> candidates = resolveCandidates(req);
        BizException.assertTrue(!candidates.isEmpty(), ErrorCode.WAVE_NO_CANDIDATE_ORDER);

        List<WavePreviewVO.GroupPreview> groups = buildGroups(candidates, req);
        WaveStrategy strategy = WaveStrategy.of(req.getStrategy());
        String prefix = blank(req.getNamePrefix()) ? LocalDateTime.now().toLocalDate() + " 波次" : req.getNamePrefix();

        List<WaveVO> created = new ArrayList<>();
        int idx = 1;
        for (WavePreviewVO.GroupPreview g : groups) {
            Wave wave = new Wave()
                    .setWaveNo(noGen.next("WV"))
                    .setWaveName(prefix + " " + String.format("%02d", idx++) + "（" + g.getGroupKey() + "）")
                    .setStrategy(strategy)
                    .setStatus(WaveStatus.CREATED)
                    .setOrderCount(g.getOrders().size())
                    .setProvinceScope(g.getGroupKey())
                    .setCreatorNo(req.getCreatorNo())
                    .setCreatorName(req.getCreatorName())
                    .setHasShortage(false);

            int totalQty = 0;
            Set<String> skuSet = new LinkedHashSet<>();
            Set<String> locSet = new LinkedHashSet<>();
            for (OrderInfo o : g.getOrders()) {
                for (OrderItem it : orderItemRepo.find(i -> i.getOrderNo().equals(o.getOrderNo()))) {
                    skuSet.add(it.getSkuCode());
                    locSet.add(it.getLocationCode());
                    totalQty += it.getQty();
                }
            }
            wave.setSkuCount(skuSet.size()).setTotalQty(totalQty).setLocationCount(locSet.size());
            wave = waveRepo.save(wave);

            // 关联订单 + 推进订单状态
            int seq = 1;
            for (OrderInfo o : g.getOrders()) {
                waveOrderRepo.save(new WaveOrder()
                        .setWaveNo(wave.getWaveNo()).setOrderNo(o.getOrderNo()).setSeqNo(seq++).setHeld(false));
                o.setWaveNo(wave.getWaveNo()).setStatus(OrderStatus.WAVED);
                orderRepo.save(o);
            }

            // 生成拣货任务与明细
            createPickTask(wave, g.getOrders());

            saveLog("WAVE", wave.getWaveNo(), "WAVE_CREATE", "生成波次",
                    req.getCreatorNo(), req.getCreatorName(), "波次员",
                    "策略=" + strategy.getDesc() + "，订单 " + g.getOrders().size() + " 单，"
                            + skuSet.size() + " 个 SKU，共 " + totalQty + " 件");

            // 可选：立即分配拣货员
            if (Boolean.TRUE.equals(req.getAutoAssign())) {
                assignPickerInternal(wave, null);
                wave = findWave(wave.getWaveNo());
            }
            created.add(toVO(wave));
        }

        log.info("[波次生成] 策略={} 生成 {} 个波次，覆盖 {} 笔订单",
                strategy.getDesc(), created.size(), candidates.size());
        return created;
    }

    /* ====================================================================== */
    /* 波次作业动作                                                             */
    /* ====================================================================== */

    /** 分配拣货员。 */
    public WaveVO assignPicker(WaveAssignRequest req) {
        Wave wave = findWave(req.getWaveNo());
        BizException.assertTrue(wave.getStatus() == WaveStatus.CREATED
                        || wave.getStatus() == WaveStatus.ASSIGNED,
                ErrorCode.WAVE_STATUS_ILLEGAL, "只有「待分配 / 已分配」的波次可以改派拣货员");

        Employee picker = employeeRepo.findOne(e -> e.getEmployeeNo().equals(req.getPickerNo()))
                .orElseThrow(() -> new BizException(ErrorCode.DATA_NOT_FOUND, "拣货员不存在: " + req.getPickerNo()));
        BizException.assertTrue(picker.getRole() == EmployeeRole.PICKER,
                ErrorCode.OPERATION_NOT_ALLOWED, picker.getEmployeeName() + " 不是拣货员");

        wave.setPickerNo(picker.getEmployeeNo())
                .setPickerName(picker.getEmployeeName())
                .setStatus(WaveStatus.ASSIGNED)
                .setAssignTime(LocalDateTime.now());
        Wave saved = waveRepo.save(wave);
        final String waveNo = saved.getWaveNo();

        // 同步拣货任务的负责人
        for (PickTask t : pickTaskRepo.find(t -> waveNo.equals(t.getWaveNo()))) {
            t.setPickerNo(picker.getEmployeeNo()).setPickerName(picker.getEmployeeName());
            pickTaskRepo.save(t);
        }

        saveLog("WAVE", waveNo, "WAVE_ASSIGN", "分配拣货员",
                req.getOperatorNo(), req.getOperatorName(), "波次员",
                "拣货员=" + picker.getEmployeeName());
        return toVO(saved);
    }

    /** 按当前负载自动挑一个最闲的拣货员（负载均衡）。 */
    private void assignPickerInternal(Wave wave, String operatorNo) {
        Optional<Map<String, Object>> lightest = pickerWorkload().stream().findFirst();
        if (lightest.isEmpty()) {
            log.warn("[波次分配] 无可用拣货员，波次 {} 保持待分配", wave.getWaveNo());
            return;
        }
        String pickerNo = (String) lightest.get().get("pickerNo");
        assignPicker(new WaveAssignRequest().setWaveNo(wave.getWaveNo()).setPickerNo(pickerNo)
                .setOperatorNo(operatorNo).setOperatorName("系统自动分配"));
    }

    /** 拣货完成（由 {@link PickService} 调用）。 */
    public Wave markPicked(String waveNo) {
        Wave wave = findWave(waveNo);
        wave.setStatus(WaveStatus.PICKED)
                .setPickFinishTime(LocalDateTime.now());
        if (wave.getPickStartTime() != null) {
            wave.setPickDurationSec(Duration.between(wave.getPickStartTime(), wave.getPickFinishTime()).getSeconds());
        }
        return waveRepo.save(wave);
    }

    /** 拣货开工（由 {@link PickService} 调用）。 */
    public Wave markPickStart(String waveNo) {
        Wave wave = findWave(waveNo);
        if (wave.getPickStartTime() == null) {
            wave.setPickStartTime(LocalDateTime.now());
        }
        if (wave.getStatus() == WaveStatus.ASSIGNED || wave.getStatus() == WaveStatus.CREATED) {
            wave.setStatus(WaveStatus.PICKING);
        }
        return waveRepo.save(wave);
    }

    /** 标记波次含缺货（由 {@link PickService} 调用）。 */
    public Wave markHasShortage(String waveNo) {
        Wave wave = findWave(waveNo);
        wave.setHasShortage(true);
        return waveRepo.save(wave);
    }

    /** 复核完成（由 {@link ReviewService} 调用）。 */
    public Wave markReviewed(String waveNo) {
        Wave wave = findWave(waveNo);
        wave.setStatus(WaveStatus.REVIEWED).setReviewFinishTime(LocalDateTime.now());
        return waveRepo.save(wave);
    }

    /** 交接完成（由 {@link HandoverService} 调用）。 */
    public Wave markHandovered(String waveNo, LocalDateTime handoverTime) {
        Wave wave = findWave(waveNo);
        wave.setStatus(WaveStatus.HANDOVERED).setHandoverTime(handoverTime);
        if (wave.getCreateTime() != null) {
            wave.setTotalDurationSec(Duration.between(wave.getCreateTime(), handoverTime).getSeconds());
        }
        return waveRepo.save(wave);
    }

    /** 归档波次。 */
    public WaveVO closeWave(String waveNo) {
        Wave wave = findWave(waveNo);
        BizException.assertTrue(wave.getStatus() == WaveStatus.HANDOVERED,
                ErrorCode.WAVE_STATUS_ILLEGAL, "只有「已出库交接」的波次可以归档");
        wave.setStatus(WaveStatus.CLOSED);
        return toVO(waveRepo.save(wave));
    }

    /** 取消波次：把订单退回订单池，并清理拣货任务。 */
    public WaveVO cancelWave(String waveNo) {
        Wave wave = findWave(waveNo);
        BizException.assertTrue(wave.getStatus() == WaveStatus.CREATED || wave.getStatus() == WaveStatus.ASSIGNED,
                ErrorCode.WAVE_STATUS_ILLEGAL, "只有「待分配 / 已分配」的波次可以取消");

        List<WaveOrder> relations = waveOrderRepo.find(r -> waveNo.equals(r.getWaveNo()));
        for (WaveOrder r : relations) {
            orderRepo.findOne(o -> o.getOrderNo().equals(r.getOrderNo())).ifPresent(o -> {
                o.setStatus(OrderStatus.POOLED).setWaveNo(null);
                orderRepo.save(o);
            });
            waveOrderRepo.deleteById(r.getId());
        }
        for (PickTask t : pickTaskRepo.find(t -> waveNo.equals(t.getWaveNo()))) {
            pickItemRepo.find(i -> t.getTaskNo().equals(i.getTaskNo()))
                    .forEach(i -> pickItemRepo.deleteById(i.getId()));
            pickTaskRepo.deleteById(t.getId());
        }
        wave.setStatus(WaveStatus.CANCELLED);
        wave = waveRepo.save(wave);

        saveLog("WAVE", waveNo, "WAVE_CANCEL", "取消波次", null, null, "波次员",
                "退回 " + relations.size() + " 笔订单至订单池");
        return toVO(wave);
    }

    /* ====================================================================== */
    /* 拣货任务生成（核心：库位合并 + 路径排序）                                   */
    /* ====================================================================== */

    /**
     * 为一个波次生成拣货任务与明细。
     *
     * <p>合并规则：波次内所有订单的明细，按「库位 + SKU」聚合数量。
     * 这样拣货员在同一库位只需停一次，就能满足多个订单的需求 —— 这是波次拣货的效率核心。
     */
    public PickTask createPickTask(Wave wave, List<OrderInfo> orders) {
        // 库位路径序，用于排序拣货路线
        Map<String, Integer> pathSeq = locationRepo.findAll().stream()
                .filter(l -> l.getPathSeq() != null)
                .collect(Collectors.toMap(WarehouseLocation::getLocationCode, WarehouseLocation::getPathSeq,
                        (a, b) -> a));

        // 合并：key = 库位|SKU
        Map<String, PickItem> merged = new LinkedHashMap<>();
        Map<String, Set<String>> orderNosOfKey = new HashMap<>();
        for (OrderInfo o : orders) {
            for (OrderItem it : orderItemRepo.find(i -> i.getOrderNo().equals(o.getOrderNo()))) {
                String key = it.getLocationCode() + "|" + it.getSkuCode();
                PickItem pi = merged.get(key);
                if (pi == null) {
                    pi = new PickItem()
                            .setWaveNo(wave.getWaveNo())
                            .setLocationCode(it.getLocationCode())
                            .setSkuCode(it.getSkuCode())
                            .setSkuName(it.getSkuName())
                            .setSpec(it.getSpec())
                            .setUnit(it.getUnit())
                            .setShouldQty(0).setActualQty(0).setShortageQty(0)
                            .setStatus(PickItemStatus.PENDING);
                    merged.put(key, pi);
                    orderNosOfKey.put(key, new LinkedHashSet<>());
                }
                pi.setShouldQty(pi.getShouldQty() + it.getQty());
                orderNosOfKey.get(key).add(o.getOrderNo());
            }
        }

        // 按库位路径排序（未知库位排在最后）
        List<PickItem> items = new ArrayList<>(merged.values());
        items.sort(Comparator.comparingInt(pi -> pathSeq.getOrDefault(pi.getLocationCode(), Integer.MAX_VALUE)));

        String taskNo = "PK" + wave.getWaveNo().substring(Math.max(0, wave.getWaveNo().length() - 12));
        int seq = 1;
        int shouldTotal = 0;
        Set<String> locs = new LinkedHashSet<>();
        for (PickItem pi : items) {
            pi.setTaskNo(taskNo).setSeqNo(seq++);
            pi.setRelatedOrderNos(String.join(",", orderNosOfKey.get(pi.getLocationCode() + "|" + pi.getSkuCode())));
            pickItemRepo.save(pi);
            shouldTotal += pi.getShouldQty();
            locs.add(pi.getLocationCode());
        }

        PickTask task = new PickTask()
                .setTaskNo(taskNo)
                .setWaveNo(wave.getWaveNo())
                .setPickerNo(wave.getPickerNo())
                .setPickerName(wave.getPickerName())
                .setStatus(PickTaskStatus.WAITING)
                .setItemCount(items.size())
                .setFinishedCount(0)
                .setLocationCount(locs.size())
                .setShouldQty(shouldTotal)
                .setActualQty(0)
                .setShortageQty(0)
                .setOrderCount(orders.size())
                .setPathText(String.join(" → ", locs));
        task.setCreateTime(LocalDateTime.now());
        return pickTaskRepo.save(task);
    }

    /* ====================================================================== */
    /* 内部：候选订单与分组                                                      */
    /* ====================================================================== */

    /** 解析参与生成的候选订单。 */
    private List<OrderInfo> resolveCandidates(WaveGenerateRequest req) {
        List<OrderInfo> candidates;
        if (req.getOrderNos() != null && !req.getOrderNos().isEmpty()) {
            candidates = orderRepo.findAll().stream()
                    .filter(o -> req.getOrderNos().contains(o.getOrderNo()))
                    .filter(o -> o.getStatus() != null && o.getStatus().isPoolable())
                    .collect(Collectors.toList());
        } else {
            candidates = orderRepo.find(o -> o.getStatus() == OrderStatus.POOLED);
        }
        // 加急订单优先进入波次
        candidates.sort(Comparator.comparing((OrderInfo o) -> !"URGENT".equals(o.getPriority()))
                .thenComparing(o -> o.getPayTime() == null ? LocalDateTime.MIN : o.getPayTime()));
        return candidates;
    }

    /**
     * 按策略分组，并按容量上限切分。
     */
    private List<WavePreviewVO.GroupPreview> buildGroups(List<OrderInfo> candidates, WaveGenerateRequest req) {
        WaveStrategy strategy = WaveStrategy.of(req.getStrategy());
        int maxOrders = req.getMaxOrdersPerWave() == null ? 12 : req.getMaxOrdersPerWave();
        int maxSkus = req.getMaxSkusPerWave() == null ? 30 : req.getMaxSkusPerWave();
        int maxQty = req.getMaxQtyPerWave() == null ? 120 : req.getMaxQtyPerWave();

        // 1) 先按策略聚类
        Map<String, List<OrderInfo>> grouped = new LinkedHashMap<>();
        for (OrderInfo o : candidates) {
            String key = switch (strategy) {
                case BY_AREA -> o.getProvince() == null ? "未知区域" : o.getProvince();
                case BY_CARRIER -> o.getCarrierName() == null ? "未知承运商" : o.getCarrierName();
                case BY_TIMELINESS -> "URGENT".equals(o.getPriority()) ? "加急订单" : "普通订单";
                case BY_ORDER_COUNT -> "均衡批次";
            };
            grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(o);
        }

        // 2) 组内按容量切分
        List<WavePreviewVO.GroupPreview> result = new ArrayList<>();
        for (Map.Entry<String, List<OrderInfo>> e : grouped.entrySet()) {
            result.addAll(splitByCapacity(e.getKey(), e.getValue(), maxOrders, maxSkus, maxQty, strategy));
        }
        return result;
    }

    /** 把一组订单按「最大订单数 / 最大 SKU 数 / 最大件数」切成若干波次。 */
    private List<WavePreviewVO.GroupPreview> splitByCapacity(String groupKey, List<OrderInfo> orders,
                                                             int maxOrders, int maxSkus, int maxQty,
                                                             WaveStrategy strategy) {
        List<WavePreviewVO.GroupPreview> result = new ArrayList<>();
        List<OrderInfo> current = new ArrayList<>();
        Set<String> skuSet = new LinkedHashSet<>();
        Set<String> locSet = new LinkedHashSet<>();
        int qty = 0;
        int batch = 1;

        for (OrderInfo o : orders) {
            List<OrderItem> items = orderItemRepo.find(i -> i.getOrderNo().equals(o.getOrderNo()));
            Set<String> orderSkus = items.stream().map(OrderItem::getSkuCode).collect(Collectors.toSet());
            Set<String> orderLocs = items.stream().map(OrderItem::getLocationCode).collect(Collectors.toSet());
            int orderQty = items.stream().mapToInt(OrderItem::getQty).sum();

            Set<String> mergedSkus = new LinkedHashSet<>(skuSet);
            mergedSkus.addAll(orderSkus);
            boolean overflow = !current.isEmpty()
                    && (current.size() + 1 > maxOrders
                    || mergedSkus.size() > maxSkus
                    || qty + orderQty > maxQty);

            if (overflow) {
                result.add(buildGroupPreview(groupKey, batch++, current, skuSet, locSet, qty, strategy));
                current = new ArrayList<>();
                skuSet = new LinkedHashSet<>();
                locSet = new LinkedHashSet<>();
                qty = 0;
            }
            current.add(o);
            skuSet.addAll(orderSkus);
            locSet.addAll(orderLocs);
            qty += orderQty;
        }
        if (!current.isEmpty()) {
            result.add(buildGroupPreview(groupKey, batch, current, skuSet, locSet, qty, strategy));
        }
        return result;
    }

    private WavePreviewVO.GroupPreview buildGroupPreview(String groupKey, int batch, List<OrderInfo> orders,
                                                         Set<String> skuSet, Set<String> locSet, int qty,
                                                         WaveStrategy strategy) {
        String key = batch > 1 ? groupKey + " #" + batch : groupKey;
        return new WavePreviewVO.GroupPreview()
                .setGroupKey(key)
                .setSuggestWaveName(LocalDateTime.now().toLocalDate() + " " + key)
                .setOrderCount(orders.size())
                .setSkuCount(skuSet.size())
                .setTotalQty(qty)
                .setLocationCount(locSet.size())
                .setOverCapacity(false)
                .setOrders(new ArrayList<>(orders));
    }

    /* ====================================================================== */
    /* 内部：视图转换与工具                                                      */
    /* ====================================================================== */

    public Wave findWave(String waveNo) {
        return waveRepo.findOne(w -> w.getWaveNo().equals(waveNo))
                .orElseThrow(() -> new BizException(ErrorCode.WAVE_NOT_FOUND, "波次不存在: " + waveNo));
    }

    public WaveVO toVO(Wave wave) {
        WaveVO vo = new WaveVO();
        vo.setId(wave.getId());
        vo.setCreateTime(wave.getCreateTime());
        vo.setUpdateTime(wave.getUpdateTime());
        vo.setDeleted(wave.getDeleted());
        vo.setWaveNo(wave.getWaveNo());
        vo.setWaveName(wave.getWaveName());
        vo.setStrategy(wave.getStrategy());
        vo.setStatus(wave.getStatus());
        vo.setOrderCount(wave.getOrderCount());
        vo.setSkuCount(wave.getSkuCount());
        vo.setTotalQty(wave.getTotalQty());
        vo.setLocationCount(wave.getLocationCount());
        vo.setProvinceScope(wave.getProvinceScope());
        vo.setCreatorNo(wave.getCreatorNo());
        vo.setCreatorName(wave.getCreatorName());
        vo.setPickerNo(wave.getPickerNo());
        vo.setPickerName(wave.getPickerName());
        vo.setReviewerNo(wave.getReviewerNo());
        vo.setReviewerName(wave.getReviewerName());
        vo.setAssignTime(wave.getAssignTime());
        vo.setPickStartTime(wave.getPickStartTime());
        vo.setPickFinishTime(wave.getPickFinishTime());
        vo.setReviewFinishTime(wave.getReviewFinishTime());
        vo.setHandoverTime(wave.getHandoverTime());
        vo.setPickDurationSec(wave.getPickDurationSec());
        vo.setTotalDurationSec(wave.getTotalDurationSec());
        vo.setHasShortage(wave.getHasShortage());
        vo.setRemark(wave.getRemark());

        vo.setStatusDesc(wave.getStatus() == null ? "" : wave.getStatus().getDesc());
        vo.setStrategyDesc(wave.getStrategy() == null ? "" : wave.getStrategy().getDesc());
        vo.setProgress(progressOf(wave.getStatus()));
        vo.setCurrentNode(nodeOf(wave.getStatus()));
        vo.setOrders(waveOrderRepo.find(r -> wave.getWaveNo().equals(r.getWaveNo())).stream()
                .sorted(Comparator.comparing(WaveOrder::getSeqNo))
                .map(r -> orderRepo.findOne(o -> o.getOrderNo().equals(r.getOrderNo())).orElse(null))
                .filter(Objects::nonNull)
                .collect(Collectors.toList()));
        return vo;
    }

    private static int progressOf(WaveStatus s) {
        if (s == null) {
            return 0;
        }
        return switch (s) {
            case CREATED -> 5;
            case ASSIGNED -> 15;
            case PICKING -> 40;
            case PICKED -> 60;
            case REVIEWING -> 75;
            case REVIEWED -> 85;
            case HANDOVERED -> 95;
            case CLOSED -> 100;
            case CANCELLED -> 0;
        };
    }

    private static String nodeOf(WaveStatus s) {
        if (s == null) {
            return "";
        }
        return switch (s) {
            case CREATED, ASSIGNED -> "波次生成";
            case PICKING -> "拣货执行";
            case PICKED, REVIEWING -> "复核打包";
            case REVIEWED -> "出库交接";
            case HANDOVERED, CLOSED -> "已完成";
            case CANCELLED -> "已取消";
        };
    }

    private void saveLog(String bizType, String bizNo, String action, String actionDesc,
                         String operatorNo, String operatorName, String operatorRole, String detail) {
        OperationLog ol = new OperationLog()
                .setBizType(bizType).setBizNo(bizNo).setAction(action).setActionDesc(actionDesc)
                .setOperatorNo(operatorNo).setOperatorName(operatorName).setOperatorRole(operatorRole)
                .setDetail(detail).setOperateTime(LocalDateTime.now());
        ol.setCreateTime(LocalDateTime.now());
        logRepo.save(ol);
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
