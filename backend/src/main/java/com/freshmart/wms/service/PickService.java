package com.freshmart.wms.service;

import com.freshmart.wms.common.BizException;
import com.freshmart.wms.common.BizNoGenerator;
import com.freshmart.wms.common.ErrorCode;
import com.freshmart.wms.common.PageResult;
import com.freshmart.wms.domain.entity.*;
import com.freshmart.wms.domain.enums.*;
import com.freshmart.wms.dto.PickConfirmRequest;
import com.freshmart.wms.dto.PickShortageRequest;
import com.freshmart.wms.dto.PickTaskQuery;
import com.freshmart.wms.repository.*;
import com.freshmart.wms.vo.PickTaskVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 拣货服务 —— 闭环第三环「拣货执行」。
 *
 * <p>拣货员的操作序列：领取任务 → 逐行扫码确认实拣数量（或上报缺货）→ 全部行处理完自动结单。
 * 结单时会把实拣数量回写到订单明细，并把波次推进到「拣货完成」，等待复核。
 *
 * <p>缺货上报不阻断拣货：明细行标记为「缺货 / 部分拣货」，波次打上缺货标记，
 * 由波次员在缺货处理页面统一决策（拆单 / 取消 / 替代 / 等补货），拣货员可继续拣下一行。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PickService {

    private final PickTaskRepository taskRepo;
    private final PickItemRepository itemRepo;
    private final ShortageRepository shortageRepo;
    private final WaveOrderRepository waveOrderRepo;
    private final OrderRepository orderRepo;
    private final OrderItemRepository orderItemRepo;
    private final OperationLogRepository logRepo;
    private final WaveService waveService;
    private final BizNoGenerator noGen;

    /* ====================================================================== */
    /* 查询                                                                    */
    /* ====================================================================== */

    public PageResult<PickTaskVO> pageTasks(PickTaskQuery query) {
        List<PickTaskVO> all = taskRepo.findAll().stream()
                .filter(t -> blank(query.getTaskNo())
                        || t.getTaskNo().toLowerCase().contains(query.getTaskNo().toLowerCase()))
                .filter(t -> blank(query.getWaveNo()) || query.getWaveNo().equals(t.getWaveNo()))
                .filter(t -> blank(query.getPickerNo()) || query.getPickerNo().equals(t.getPickerNo()))
                .filter(t -> blank(query.getStatus())
                        || (t.getStatus() != null && query.getStatus().equals(t.getStatus().getCode())))
                .map(this::toVO)
                .collect(Collectors.toList());

        // 未完成的任务排前面
        all.sort(Comparator.comparing((PickTaskVO v) -> v.getStatus() == PickTaskStatus.FINISHED)
                .thenComparing(v -> v.getCreateTime() == null ? LocalDateTime.MIN : v.getCreateTime(),
                        Comparator.reverseOrder()));
        return PageResult.of(all, query);
    }

    public PickTaskVO getTask(String taskNo) {
        return toVO(findTask(taskNo));
    }

    /** 拣货员的「我的任务」列表。 */
    public List<PickTaskVO> myTasks(String pickerNo) {
        return taskRepo.find(t -> pickerNo.equals(t.getPickerNo())
                        && t.getStatus() != PickTaskStatus.FINISHED
                        && t.getStatus() != PickTaskStatus.CANCELLED)
                .stream().map(this::toVO).collect(Collectors.toList());
    }

    /* ====================================================================== */
    /* 作业动作                                                                 */
    /* ====================================================================== */

    /** 领取任务并开工。 */
    @Transactional
    public PickTaskVO startTask(String taskNo, String pickerNo) {
        PickTask task = findTask(taskNo);
        BizException.assertTrue(task.getStatus() == PickTaskStatus.WAITING
                        || task.getStatus() == PickTaskStatus.PICKING,
                ErrorCode.PICK_TASK_STATUS_ILLEGAL, "任务当前状态不允许开工");
        if (!blank(pickerNo)) {
            BizException.assertTrue(pickerNo.equals(task.getPickerNo()),
                    ErrorCode.PICK_NOT_OWNER, "该任务已分配给 " + task.getPickerName());
        }
        if (task.getStatus() == PickTaskStatus.WAITING) {
            task.setStatus(PickTaskStatus.PICKING).setStartTime(LocalDateTime.now());
            task = taskRepo.save(task);
            waveService.markPickStart(task.getWaveNo());
            saveLog("PICK", taskNo, "PICK_START", "开始拣货",
                    task.getPickerNo(), task.getPickerName(), "拣货员", "波次 " + task.getWaveNo());
        }
        return toVO(task);
    }

    /**
     * 确认某一行明细的实拣数量。
     *
     * <p>实拣 = 应拣 → 该行置为「已拣齐」；
     * 实拣 &lt; 应拣 → 该行置为「部分拣货」并自动生成缺货记录（差额部分）；
     * 实拣 &gt; 应拣 → 视为异常，直接拒绝，避免把多拣的货带出仓库。
     */
    @Transactional
    public PickTaskVO confirmItem(PickConfirmRequest req) {
        PickItem item = itemRepo.findById(req.getItemId())
                .orElseThrow(() -> new BizException(ErrorCode.DATA_NOT_FOUND, "拣货明细不存在"));
        PickTask task = findTask(item.getTaskNo());

        BizException.assertTrue(task.getStatus() == PickTaskStatus.PICKING
                        || task.getStatus() == PickTaskStatus.WAITING,
                ErrorCode.PICK_TASK_STATUS_ILLEGAL, "任务已结单，不能继续确认");

        BizException.assertTrue(req.getActualQty() <= item.getShouldQty(),
                ErrorCode.PICK_QTY_ILLEGAL,
                "实拣 " + req.getActualQty() + " 件超过应拣 " + item.getShouldQty() + " 件，请改用缺货上报或多货处理");

        item.setActualQty(req.getActualQty()).setScanTime(LocalDateTime.now());
        if (req.getActualQty().equals(item.getShouldQty())) {
            item.setStatus(PickItemStatus.PICKED).setShortageQty(0);
        } else {
            int shortQty = item.getShouldQty() - req.getActualQty();
            item.setShortageQty(shortQty).setStatus(PickItemStatus.PARTIAL);
            createShortage(item, shortQty, ShortageReason.STOCK_ZERO, task, req.getRemark());
        }
        if (!blank(req.getRemark())) {
            item.setRemark(req.getRemark());
        }
        itemRepo.save(item);

        recalcTask(task);
        return toVO(findTask(task.getTaskNo()));
    }

    /**
     * 上报缺货：拣货员在库位现场发现实物不足，直接报缺。
     */
    @Transactional
    public PickTaskVO reportShortage(PickShortageRequest req) {
        PickItem item = itemRepo.findById(req.getItemId())
                .orElseThrow(() -> new BizException(ErrorCode.DATA_NOT_FOUND, "拣货明细不存在"));
        PickTask task = findTask(item.getTaskNo());

        BizException.assertTrue(task.getStatus() == PickTaskStatus.PICKING
                        || task.getStatus() == PickTaskStatus.WAITING,
                ErrorCode.PICK_TASK_STATUS_ILLEGAL, "任务已结单，不能上报缺货");
        BizException.assertTrue(req.getShortageQty() <= item.getShouldQty(),
                ErrorCode.PICK_QTY_ILLEGAL, "缺货数量不能大于应拣数量");

        int actual = item.getShouldQty() - req.getShortageQty();
        item.setShortageQty(req.getShortageQty()).setActualQty(actual)
                .setStatus(actual == 0 ? PickItemStatus.SHORTAGE : PickItemStatus.PARTIAL)
                .setScanTime(LocalDateTime.now());
        if (!blank(req.getRemark())) {
            item.setRemark(req.getRemark());
        }
        itemRepo.save(item);

        createShortage(item, req.getShortageQty(), ShortageReason.of(req.getReason()), task, req.getRemark());

        // 波次打上缺货标记
        waveService.markHasShortage(task.getWaveNo());
        waveService.markPickStart(task.getWaveNo());

        recalcTask(task);
        log.warn("[缺货上报] 任务 {} 库位 {} SKU {} 缺 {} 件", task.getTaskNo(),
                item.getLocationCode(), item.getSkuCode(), req.getShortageQty());
        return toVO(findTask(task.getTaskNo()));
    }

    /**
     * 手工结单。若仍有未处理的明细行，会拒绝并提示。
     */
    @Transactional
    public PickTaskVO finishTask(String taskNo) {
        PickTask task = findTask(taskNo);
        BizException.assertTrue(task.getStatus() != PickTaskStatus.FINISHED,
                ErrorCode.PICK_TASK_STATUS_ILLEGAL, "任务已结单");

        List<PickItem> items = itemRepo.find(i -> taskNo.equals(i.getTaskNo()));
        long pending = items.stream().filter(i -> i.getStatus() == PickItemStatus.PENDING).count();
        BizException.assertTrue(pending == 0, ErrorCode.PICK_TASK_NOT_FINISHED,
                "还有 " + pending + " 行未处理，请先逐行确认或上报缺货");

        return toVO(doFinish(task, items));
    }

    /* ====================================================================== */
    /* 内部逻辑                                                                 */
    /* ====================================================================== */

    /** 结单：写时间戳、回写订单明细、推进波次状态。 */
    private PickTask doFinish(PickTask task, List<PickItem> items) {
        LocalDateTime now = LocalDateTime.now();
        task.setStatus(task.getShortageQty() != null && task.getShortageQty() > 0
                        ? PickTaskStatus.EXCEPTION : PickTaskStatus.FINISHED)
                .setFinishTime(now);
        if (task.getStartTime() != null) {
            task.setDurationSec(Duration.between(task.getStartTime(), now).getSeconds());
        }
        PickTask saved = taskRepo.save(task);
        final String waveNo = saved.getWaveNo();

        // 回写订单明细的实拣数量（按明细行覆盖，缺货行按比例摊到关联订单）
        for (WaveOrder wo : waveOrderRepo.find(r -> waveNo.equals(r.getWaveNo()))) {
            for (OrderItem oi : orderItemRepo.find(i -> i.getOrderNo().equals(wo.getOrderNo()))) {
                int picked = items.stream()
                        .filter(pi -> pi.getSkuCode().equals(oi.getSkuCode())
                                && pi.getRelatedOrderNos() != null
                                && pi.getRelatedOrderNos().contains(wo.getOrderNo()))
                        .mapToInt(pi -> {
                            // 若该明细行只服务这一个订单，直接取实拣数；否则按订单需求占比折算
                            String[] nos = pi.getRelatedOrderNos().split(",");
                            if (nos.length == 1) {
                                return pi.getActualQty() == null ? 0 : pi.getActualQty();
                            }
                            int shouldSum = nos.length == 0 ? oi.getQty()
                                    : orderItemRepo.find(x -> x.getSkuCode().equals(oi.getSkuCode())
                                            && List.of(nos).contains(x.getOrderNo()))
                                    .stream().mapToInt(OrderItem::getQty).sum();
                            int actual = pi.getActualQty() == null ? 0 : pi.getActualQty();
                            return shouldSum <= 0 ? 0 : (int) Math.round(actual * (oi.getQty() * 1.0 / shouldSum));
                        }).sum();
                oi.setPickedQty(Math.min(picked, oi.getQty()));
                orderItemRepo.save(oi);
            }
            orderRepo.findOne(o -> o.getOrderNo().equals(wo.getOrderNo())).ifPresent(o -> {
                o.setStatus(OrderStatus.PICKED);
                orderRepo.save(o);
            });
        }

        waveService.markPicked(waveNo);
        saveLog("PICK", saved.getTaskNo(), "PICK_FINISH", "拣货完成",
                saved.getPickerNo(), saved.getPickerName(), "拣货员",
                "实拣 " + saved.getActualQty() + " / 应拣 " + saved.getShouldQty() + " 件"
                        + (saved.getShortageQty() != null && saved.getShortageQty() > 0
                        ? "，缺货 " + saved.getShortageQty() + " 件" : ""));
        return saved;
    }

    /** 重算任务的汇总字段；若所有行都已处理则自动结单。 */
    private void recalcTask(PickTask task) {
        List<PickItem> items = itemRepo.find(i -> task.getTaskNo().equals(i.getTaskNo()));
        task.setFinishedCount((int) items.stream()
                .filter(i -> i.getStatus() != PickItemStatus.PENDING).count());
        task.setActualQty(items.stream().mapToInt(i -> i.getActualQty() == null ? 0 : i.getActualQty()).sum());
        task.setShortageQty(items.stream().mapToInt(i -> i.getShortageQty() == null ? 0 : i.getShortageQty()).sum());
        task.setItemCount(items.size());

        boolean allProcessed = items.stream().noneMatch(i -> i.getStatus() == PickItemStatus.PENDING);
        if (allProcessed && task.getStatus() != PickTaskStatus.FINISHED) {
            // 首行确认即视为开工
            if (task.getStartTime() == null) {
                task.setStartTime(LocalDateTime.now());
                waveService.markPickStart(task.getWaveNo());
            }
            taskRepo.save(task);
            doFinish(task, items);
        } else {
            if (task.getStatus() == PickTaskStatus.WAITING) {
                task.setStatus(PickTaskStatus.PICKING);
            }
            if (task.getStartTime() == null) {
                task.setStartTime(LocalDateTime.now());
                waveService.markPickStart(task.getWaveNo());
            }
            if (task.getShortageQty() != null && task.getShortageQty() > 0) {
                task.setStatus(PickTaskStatus.EXCEPTION);
            }
            taskRepo.save(task);
        }
    }

    /** 生成缺货记录（同一明细行重复上报时累加，不重复建单）。 */
    private void createShortage(PickItem item, int shortQty, ShortageReason reason,
                                PickTask task, String remark) {
        ShortageRecord exist = shortageRepo.findOne(s ->
                task.getTaskNo().equals(s.getTaskNo())
                        && item.getSkuCode().equals(s.getSkuCode())
                        && item.getLocationCode().equals(s.getLocationCode())
                        && s.getStatus() == ShortageStatus.PENDING).orElse(null);

        String firstOrder = item.getRelatedOrderNos() == null ? null
                : item.getRelatedOrderNos().split(",")[0];

        if (exist != null) {
            exist.setShortageQty(shortQty).setReason(reason);
            if (!blank(remark)) {
                exist.setHandleRemark(remark);
            }
            shortageRepo.save(exist);
            return;
        }

        ShortageRecord sr = new ShortageRecord()
                .setShortageNo(noGen.next("SH"))
                .setWaveNo(item.getWaveNo())
                .setTaskNo(task.getTaskNo())
                .setOrderNo(firstOrder)
                .setLocationCode(item.getLocationCode())
                .setSkuCode(item.getSkuCode())
                .setSkuName(item.getSkuName())
                .setShouldQty(item.getShouldQty())
                .setShortageQty(shortQty)
                .setReason(reason)
                .setReporterNo(task.getPickerNo())
                .setReporterName(task.getPickerName())
                .setReportTime(LocalDateTime.now())
                .setStatus(ShortageStatus.PENDING);
        sr.setCreateTime(LocalDateTime.now());
        shortageRepo.save(sr);
    }

    private PickTask findTask(String taskNo) {
        return taskRepo.findOne(t -> t.getTaskNo().equals(taskNo))
                .orElseThrow(() -> new BizException(ErrorCode.PICK_TASK_NOT_FOUND, "拣货任务不存在: " + taskNo));
    }

    public PickTaskVO toVO(PickTask task) {
        PickTaskVO vo = new PickTaskVO();
        vo.setId(task.getId());
        vo.setCreateTime(task.getCreateTime());
        vo.setUpdateTime(task.getUpdateTime());
        vo.setDeleted(task.getDeleted());
        vo.setTaskNo(task.getTaskNo());
        vo.setWaveNo(task.getWaveNo());
        vo.setPickerNo(task.getPickerNo());
        vo.setPickerName(task.getPickerName());
        vo.setStatus(task.getStatus());
        vo.setItemCount(task.getItemCount());
        vo.setFinishedCount(task.getFinishedCount());
        vo.setLocationCount(task.getLocationCount());
        vo.setShouldQty(task.getShouldQty());
        vo.setActualQty(task.getActualQty());
        vo.setShortageQty(task.getShortageQty());
        vo.setOrderCount(task.getOrderCount());
        vo.setStartTime(task.getStartTime());
        vo.setFinishTime(task.getFinishTime());
        vo.setDurationSec(task.getDurationSec());
        vo.setPathText(task.getPathText());
        vo.setRemark(task.getRemark());

        vo.setStatusDesc(task.getStatus() == null ? "" : task.getStatus().getDesc());
        int total = task.getItemCount() == null || task.getItemCount() == 0 ? 1 : task.getItemCount();
        vo.setProgress(Math.min(100, (int) Math.round(
                (task.getFinishedCount() == null ? 0 : task.getFinishedCount()) * 100.0 / total)));
        vo.setItems(itemRepo.find(i -> task.getTaskNo().equals(i.getTaskNo())).stream()
                .sorted(Comparator.comparing(PickItem::getSeqNo))
                .collect(Collectors.toList()));
        return vo;
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
