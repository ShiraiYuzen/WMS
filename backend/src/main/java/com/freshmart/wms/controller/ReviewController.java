package com.freshmart.wms.controller;

import com.freshmart.wms.common.PageResult;
import com.freshmart.wms.common.R;
import com.freshmart.wms.domain.entity.Wave;
import com.freshmart.wms.dto.ReviewQuery;
import com.freshmart.wms.dto.ReviewSubmitRequest;
import com.freshmart.wms.service.ReviewService;
import com.freshmart.wms.vo.ReviewVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 复核接口 —— 闭环第四环「复核打包」。
 */
@Tag(name = "05. 复核打包", description = "认领波次、逐单复核、登记差异、打包信息")
@RestController
@RequestMapping("/api/review")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    @Operation(summary = "复核记录分页查询")
    @GetMapping("/page")
    public R<PageResult<ReviewVO>> page(ReviewQuery query) {
        return R.ok(reviewService.pageReviews(query));
    }

    @Operation(summary = "复核单详情", description = "含该订单应拣清单与已记录的差异明细，供复核员对照")
    @GetMapping("/{reviewNo}")
    public R<ReviewVO> detail(@PathVariable String reviewNo) {
        return R.ok(reviewService.getReview(reviewNo));
    }

    @Operation(summary = "待复核波次列表", description = "拣货完成但尚未复核完成的波次")
    @GetMapping("/pending-waves")
    public R<List<Wave>> pendingWaves() {
        return R.ok(reviewService.pendingReviewWaves());
    }

    @Operation(summary = "认领波次", description = "为该波次内每笔订单生成待复核记录")
    @PostMapping("/claim")
    public R<List<ReviewVO>> claim(@RequestParam String waveNo,
                                   @RequestParam String reviewerNo,
                                   @RequestParam(required = false) String reviewerName) {
        return R.ok(reviewService.claimWave(waveNo, reviewerNo, reviewerName), "已认领波次");
    }

    @Operation(summary = "提交复核结果",
            description = "无差异时 diffItems 传空数组；有差异时逐条登记差异明细，系统据此累计差错率")
    @PostMapping("/submit")
    public R<ReviewVO> submit(@Valid @RequestBody ReviewSubmitRequest request) {
        return R.ok(reviewService.submit(request), "复核结果已提交");
    }
}
