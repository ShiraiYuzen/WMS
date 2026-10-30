package com.freshmart.wms.common;

import lombok.Getter;

/**
 * 业务异常。Service 层校验不通过时统一抛出该异常，由全局异常处理器转换为 {@link R}。
 */
@Getter
public class BizException extends RuntimeException {

    private final Integer code;

    public BizException(String message) {
        super(message);
        this.code = ErrorCode.OPERATION_NOT_ALLOWED.getCode();
    }

    public BizException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.code = errorCode.getCode();
    }

    public BizException(ErrorCode errorCode, String message) {
        super(message);
        this.code = errorCode.getCode();
    }

    public BizException(Integer code, String message) {
        super(message);
        this.code = code;
    }

    /** 条件成立时抛出异常，用于替代大量 if-throw 样板。 */
    public static void assertTrue(boolean condition, ErrorCode errorCode) {
        if (!condition) {
            throw new BizException(errorCode);
        }
    }

    public static void assertTrue(boolean condition, ErrorCode errorCode, String message) {
        if (!condition) {
            throw new BizException(errorCode, message);
        }
    }

    public static void assertNotNull(Object obj, ErrorCode errorCode) {
        if (obj == null) {
            throw new BizException(errorCode);
        }
    }
}
