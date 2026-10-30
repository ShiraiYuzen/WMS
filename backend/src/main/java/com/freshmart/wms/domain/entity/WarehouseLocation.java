package com.freshmart.wms.domain.entity;

import com.freshmart.wms.domain.enums.LocationStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * 仓库库位主数据。
 *
 * <p>库位编码采用四段式 {@code 区-巷道-货架-层}，例如 {@code A-01-02-03}。
 * 拣货路径优化按「区 → 巷道 → 货架 → 层」的顺序排序，
 * 保证拣货员在仓库内走单向流线，减少折返。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@Schema(description = "仓库库位")
public class WarehouseLocation extends BaseEntity {

    @Schema(description = "库位编码", example = "A-01-02-03")
    private String locationCode;

    @Schema(description = "库区", example = "A")
    private String zone;

    @Schema(description = "巷道号", example = "01")
    private String aisle;

    @Schema(description = "货架号", example = "02")
    private String rack;

    @Schema(description = "层号", example = "03")
    private String level;

    @Schema(description = "库位类型：PICK 拣货位 / BULK 存储位")
    private String locationType = "PICK";

    @Schema(description = "库位状态")
    private LocationStatus status = LocationStatus.ENABLED;

    @Schema(description = "所属承运商偏好（配送仓按承运商分区时使用）")
    private String preferredCarrierCode;

    @Schema(description = "路径排序序号，由「区-巷道-货架-层」计算得出")
    private Integer pathSeq;
}
