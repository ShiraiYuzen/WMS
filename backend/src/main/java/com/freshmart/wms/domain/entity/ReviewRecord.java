package com.freshmart.wms.domain.entity;

import com.freshmart.wms.domain.enums.DiffType;
import com.freshmart.wms.domain.enums.ReviewStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 复核记录（按订单逐单复核）。
 *
 * <p>复核员扫描订单号后，系统列出该单应含商品，复核员逐项核对。
 * 若实拣与应拣不一致，记录差异类型与差异数量，
 * 差异数据是差错率统计的直接来源。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@Schema(description = "复核记录")
public class ReviewRecord extends BaseEntity {

    @Schema(description = "复核单号", example = "RV20261015001")
    private String reviewNo;

    @Schema(description = "所属波次号")
    private String waveNo;

    @Schema(description = "订单号")
    private String orderNo;

    @Schema(description = "复核员工号")
    private String reviewerNo;

    @Schema(description = "复核员姓名")
    private String reviewerName;

    @Schema(description = "复核状态")
    private ReviewStatus status = ReviewStatus.WAITING;

    @Schema(description = "应拣 SKU 种类数")
    private Integer shouldSkuCount = 0;

    @Schema(description = "应拣总件数")
    private Integer shouldQty = 0;

    @Schema(description = "实拣总件数")
    private Integer actualQty = 0;

    @Schema(description = "差异类型")
    private DiffType diffType = DiffType.NONE;

    @Schema(description = "差异件数（绝对值合计）")
    private Integer diffQty = 0;

    @Schema(description = "差异明细描述")
    private String diffDetail;

    /* ---------------- 打包信息 ---------------- */

    @Schema(description = "包材类型：纸箱 / 气泡袋 / 保温箱")
    private String packMaterial;

    @Schema(description = "包裹件数")
    private Integer packageCount = 1;

    @Schema(description = "包裹重量（kg）")
    private Double weight;

    @Schema(description = "开始复核时间")
    private LocalDateTime startTime;

    @Schema(description = "完成复核时间")
    private LocalDateTime finishTime;

    @Schema(description = "复核耗时（秒）")
    private Long durationSec;

    @Schema(description = "备注")
    private String remark;
}
