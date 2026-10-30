package com.freshmart.wms.domain.enums;

/**
 * 带编码与中文描述的枚举统一接口。
 *
 * <p>实现该接口后，前端可直接调用 {@code /api/base/enums} 拉取全部字典项，
 * 无需在前端硬编码枚举，避免前后端字典不一致。
 */
public interface CodedEnum {

    /** 枚举编码，落库与接口传输使用该值。 */
    String getCode();

    /** 中文描述，用于界面展示。 */
    String getDesc();
}
