package com.freshmart.wms.controller;

import com.freshmart.wms.common.PageResult;
import com.freshmart.wms.common.R;
import com.freshmart.wms.dto.PickConfirmRequest;
import com.freshmart.wms.dto.PickShortageRequest;
import com.freshmart.wms.dto.PickTaskQuery;
import com.freshmart.wms.service.PickService;
import com.freshmart.wms.vo.PickTaskVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 拣货接口 —— 闭环第三环「拣货执行」。
 */
@Tag(name = "03. 拣货执行", description = "领取任务、逐行确认、缺货上报、结单")
@RestController
@RequestMapping("/api/pick")
@RequiredArgsConstructor
public class PickController {

    private final PickService pickService;

    @Operation(summary = "拣货任务分页查询")
    @GetMapping("/page")
    public R<PageResult<PickTaskVO>> page(PickTaskQuery query) {
        return R.ok(pickService.pageTasks(query));
    }

    @Operation(summary = "拣货任务详情（含按路径排序的明细行）")
    @GetMapping("/{taskNo}")
    public R<PickTaskVO> detail(@PathVariable String taskNo) {
        return R.ok(pickService.getTask(taskNo));
    }

    @Operation(summary = "我的待办任务", description = "拣货员的移动端首页用")
    @GetMapping("/my-tasks")
    public R<List<PickTaskVO>> myTasks(@RequestParam String pickerNo) {
        return R.ok(pickService.myTasks(pickerNo));
    }

    @Operation(summary = "领取任务并开工")
    @PostMapping("/{taskNo}/start")
    public R<PickTaskVO> start(@PathVariable String taskNo,
                               @RequestParam(required = false) String pickerNo) {
        return R.ok(pickService.startTask(taskNo, pickerNo), "已开始拣货");
    }

    @Operation(summary = "确认明细行实拣数量",
            description = "实拣等于应拣则置为已拣齐；少于应拣会自动生成缺货记录；多于应拣会被拒绝")
    @PostMapping("/confirm-item")
    public R<PickTaskVO> confirmItem(@Valid @RequestBody PickConfirmRequest request) {
        return R.ok(pickService.confirmItem(request));
    }

    @Operation(summary = "上报缺货", description = "库位实物不足时由拣货员现场报缺，不阻断后续拣货")
    @PostMapping("/report-shortage")
    public R<PickTaskVO> reportShortage(@Valid @RequestBody PickShortageRequest request) {
        return R.ok(pickService.reportShortage(request), "缺货已上报，等待波次员处理");
    }

    @Operation(summary = "手工结单", description = "所有明细行处理完毕后自动结单，也可手工触发")
    @PostMapping("/{taskNo}/finish")
    public R<PickTaskVO> finish(@PathVariable String taskNo) {
        return R.ok(pickService.finishTask(taskNo), "拣货任务已结单");
    }
}
