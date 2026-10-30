package com.freshmart.wms.controller;

import com.freshmart.wms.common.PageResult;
import com.freshmart.wms.common.R;
import com.freshmart.wms.dto.WaveAssignRequest;
import com.freshmart.wms.dto.WaveGenerateRequest;
import com.freshmart.wms.dto.WaveQuery;
import com.freshmart.wms.service.WaveService;
import com.freshmart.wms.vo.WavePreviewVO;
import com.freshmart.wms.vo.WaveVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 波次接口 —— 闭环第二环「波次生成」。
 */
@Tag(name = "02. 波次生成", description = "波次预览、生成、分配拣货员、状态推进")
@RestController
@RequestMapping("/api/wave")
@RequiredArgsConstructor
public class WaveController {

    private final WaveService waveService;

    @Operation(summary = "波次分页查询")
    @GetMapping("/page")
    public R<PageResult<WaveVO>> page(WaveQuery query) {
        return R.ok(waveService.pageWaves(query));
    }

    @Operation(summary = "波次详情（含订单列表与流程进度）")
    @GetMapping("/{waveNo}")
    public R<WaveVO> detail(@PathVariable String waveNo) {
        return R.ok(waveService.getWave(waveNo));
    }

    @Operation(summary = "拣货员负载看板", description = "按当前未完成波次数升序，便于波次员做负载均衡")
    @GetMapping("/picker-workload")
    public R<List<Map<String, Object>>> pickerWorkload() {
        return R.ok(waveService.pickerWorkload());
    }

    @Operation(summary = "预览波次生成结果",
            description = "不落库。按策略聚类 + 容量切分，返回会被切成几波、每波多少单，供波次员确认")
    @PostMapping("/preview")
    public R<WavePreviewVO> preview(@RequestBody WaveGenerateRequest request) {
        return R.ok(waveService.preview(request));
    }

    @Operation(summary = "正式生成波次", description = "把候选订单聚成波次，同步生成拣货任务与库位合并后的拣货明细")
    @PostMapping("/generate")
    public R<List<WaveVO>> generate(@RequestBody WaveGenerateRequest request) {
        List<WaveVO> waves = waveService.generate(request);
        return R.ok(waves, "已生成 " + waves.size() + " 个波次");
    }

    @Operation(summary = "分配拣货员")
    @PostMapping("/assign")
    public R<WaveVO> assign(@Valid @RequestBody WaveAssignRequest request) {
        return R.ok(waveService.assignPicker(request), "已分配拣货员");
    }

    @Operation(summary = "归档波次", description = "把「已出库交接」的波次归档收口")
    @PostMapping("/{waveNo}/close")
    public R<WaveVO> close(@PathVariable String waveNo) {
        return R.ok(waveService.closeWave(waveNo), "波次已归档");
    }

    @Operation(summary = "取消波次", description = "订单退回订单池，拣货任务一并清理")
    @PostMapping("/{waveNo}/cancel")
    public R<WaveVO> cancel(@PathVariable String waveNo) {
        return R.ok(waveService.cancelWave(waveNo), "波次已取消");
    }
}
