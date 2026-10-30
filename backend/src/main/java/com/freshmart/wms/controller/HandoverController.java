package com.freshmart.wms.controller;

import com.freshmart.wms.common.PageResult;
import com.freshmart.wms.common.R;
import com.freshmart.wms.domain.entity.HandoverRecord;
import com.freshmart.wms.domain.entity.Wave;
import com.freshmart.wms.dto.HandoverConfirmRequest;
import com.freshmart.wms.dto.HandoverCreateRequest;
import com.freshmart.wms.dto.HandoverQuery;
import com.freshmart.wms.service.HandoverService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 出库交接接口 —— 闭环第五环「出库交接」。
 */
@Tag(name = "06. 出库交接", description = "交接单创建与确认，完成后订单置为已出库")
@RestController
@RequestMapping("/api/handover")
@RequiredArgsConstructor
public class HandoverController {

    private final HandoverService handoverService;

    @Operation(summary = "交接单分页查询")
    @GetMapping("/page")
    public R<PageResult<HandoverRecord>> page(HandoverQuery query) {
        return R.ok(handoverService.pageHandovers(query));
    }

    @Operation(summary = "交接单详情")
    @GetMapping("/{handoverNo}")
    public R<HandoverRecord> detail(@PathVariable String handoverNo) {
        return R.ok(handoverService.getHandover(handoverNo));
    }

    @Operation(summary = "可交接波次列表", description = "已复核完成但尚未交接的波次")
    @GetMapping("/reviewable-waves")
    public R<List<Wave>> reviewableWaves(@RequestParam(required = false) String carrierCode) {
        return R.ok(handoverService.reviewableWaves(carrierCode));
    }

    @Operation(summary = "创建交接单", description = "把若干已复核波次按承运商归集成交接单")
    @PostMapping("/create")
    public R<HandoverRecord> create(@Valid @RequestBody HandoverCreateRequest request) {
        return R.ok(handoverService.create(request), "交接单已创建");
    }

    @Operation(summary = "确认交接", description = "清点无误后收口；件数不一致仍可交接但会记录异常")
    @PostMapping("/confirm")
    public R<HandoverRecord> confirm(@Valid @RequestBody HandoverConfirmRequest request) {
        return R.ok(handoverService.confirm(request), "交接已完成");
    }
}
