package com.freshmart.wms.vo;

import com.freshmart.wms.domain.entity.OrderItem;
import com.freshmart.wms.domain.entity.ReviewDiffItem;
import com.freshmart.wms.domain.entity.ReviewRecord;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;

/**
 * 复核记录视图对象。
 *
 * <p>复核员扫单后，界面需要同时看到「该单应含商品」与「已记录的差异」，
 * 因此这里把 {@code shouldItems}（应拣清单）与 {@code diffItems}（差异清单）一并返回。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@Schema(description = "复核记录视图")
public class ReviewVO extends ReviewRecord {

    @Schema(description = "复核状态中文")
    private String statusDesc;

    @Schema(description = "差异类型中文")
    private String diffTypeDesc;

    @Schema(description = "该订单应拣商品清单（复核对照用）")
    private List<OrderItem> shouldItems = new ArrayList<>();

    @Schema(description = "已记录的差异明细")
    private List<ReviewDiffItem> diffItems = new ArrayList<>();
}
