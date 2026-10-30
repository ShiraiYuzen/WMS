package com.freshmart.wms.controller;

import com.freshmart.wms.common.PageResult;
import com.freshmart.wms.common.R;
import com.freshmart.wms.domain.entity.ShortageRecord;
import com.freshmart.wms.dto.ShortageHandleRequest;
import com.freshmart.wms.dto.ShortageQuery;
import com.freshmart.wms.service.ShortageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 缺货处理接口 —— 拣货环节的异常分支。
 */
@Tag(name = "04. 缺货处理", description = "缺货记录查询与处理（拆单/取消/替代/等补货）")
@RestController
@RequestMapping("/api/shortage")
@RequiredArgsConstructor
public class ShortageController {

    private final ShortageService shortageService;

    @Operation(summary = "缺货记录分页查询")
    @GetMapping("/page")
    public R<PageResult<ShortageRecord>> page(ShortageQuery query) {
        return R.ok(shortageService.pageShortages(query));
    }

    @Operation(summary = "缺货记录详情")
    @GetMapping("/{id}")
    public R<ShortageRecord> detail(@PathVariable Long id) {
        return R.ok(shortageService.getShortage(id));
    }

    @Operation(summary = "待处理缺货数", description = "用于菜单红点提示")
    @GetMapping("/pending-count")
    public R<Long> pendingCount() {
        return R.ok(shortageService.pendingCount());
    }

    @Operation(summary = "缺货原因分布")
    @GetMapping("/reason-distribution")
    public R<Map<String, Long>> reasonDistribution() {
        return R.ok(shortageService.reasonDistribution());
    }

    @Operation(summary = "处理缺货",
            description = "处理方式：SPLIT 拆单 / CANCEL_ITEM 取消明细 / SUBSTITUTE 替代 / WAIT_REPLENISH 等补货 / CANCEL_ORDER 整单取消")
    @PostMapping("/handle")
    public R<ShortageRecord> handle(@Valid @RequestBody ShortageHandleRequest request) {
        return R.ok(shortageService.handle(request), "缺货已处理");
    }

    @Operation(summary = "归档缺货记录")
    @PostMapping("/{id}/close")
    public R<ShortageRecord> close(@PathVariable Long id) {
        return R.ok(shortageService.close(id), "已归档");
    }
}
