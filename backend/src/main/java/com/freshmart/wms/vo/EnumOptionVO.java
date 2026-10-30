package com.freshmart.wms.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 下拉字典项。前端统一从 {@code /api/base/enums} 拉取，避免字典硬编码。
 */
@Data
@Accessors(chain = true)
@Schema(description = "下拉字典项")
public class EnumOptionVO {

    @Schema(description = "枚举编码")
    private String code;

    @Schema(description = "中文描述")
    private String desc;

    @Schema(description = "补充说明（部分枚举有）")
    private String detail;

    public static EnumOptionVO of(String code, String desc) {
        return new EnumOptionVO().setCode(code).setDesc(desc);
    }

    public static EnumOptionVO of(String code, String desc, String detail) {
        return new EnumOptionVO().setCode(code).setDesc(desc).setDetail(detail);
    }
}
