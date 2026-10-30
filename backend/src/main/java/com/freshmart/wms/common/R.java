package com.freshmart.wms.common;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;

/**
 * 统一响应体。所有 Controller 一律返回该结构，前端按 {@code code === 0} 判定成功。
 *
 * @param <T> 业务数据类型
 */
@Data
@Accessors(chain = true)
@Schema(description = "统一响应体")
public class R<T> implements Serializable {

    @Schema(description = "业务状态码，0 表示成功", example = "0")
    private Integer code;

    @Schema(description = "提示信息", example = "success")
    private String message;

    @Schema(description = "业务数据")
    private T data;

    @Schema(description = "服务端时间戳（毫秒）")
    private Long timestamp;

    @Schema(description = "链路追踪号，便于日志排查")
    private String traceId;

    private R() {
        this.timestamp = System.currentTimeMillis();
    }

    public static <T> R<T> ok() {
        return ok(null);
    }

    public static <T> R<T> ok(T data) {
        R<T> r = new R<>();
        r.setCode(0).setMessage("success").setData(data);
        return r;
    }

    public static <T> R<T> ok(T data, String message) {
        R<T> r = new R<>();
        r.setCode(0).setMessage(message).setData(data);
        return r;
    }

    public static <T> R<T> fail(Integer code, String message) {
        R<T> r = new R<>();
        r.setCode(code).setMessage(message);
        return r;
    }

    public static <T> R<T> fail(ErrorCode errorCode) {
        return fail(errorCode.getCode(), errorCode.getMessage());
    }

    public static <T> R<T> fail(ErrorCode errorCode, String message) {
        return fail(errorCode.getCode(), message);
    }
}
