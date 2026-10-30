package com.freshmart.wms.common;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.util.stream.Collectors;

/**
 * 全局异常处理器。把各类异常统一转换为 {@link R} 结构，保证前端只需处理一种响应格式。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 业务异常：属于预期内的流程分支，用 warn 级别，不打堆栈。 */
    @ExceptionHandler(BizException.class)
    @ResponseStatus(HttpStatus.OK)
    public R<Void> handleBiz(BizException e, HttpServletRequest req) {
        log.warn("[业务异常] {} {} -> {}", req.getMethod(), req.getRequestURI(), e.getMessage());
        return R.fail(e.getCode(), e.getMessage());
    }

    /** @Valid 校验失败（RequestBody）。 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.OK)
    public R<Void> handleValid(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return R.fail(ErrorCode.PARAM_INVALID, msg);
    }

    /** 表单绑定校验失败。 */
    @ExceptionHandler(BindException.class)
    @ResponseStatus(HttpStatus.OK)
    public R<Void> handleBind(BindException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        return R.fail(ErrorCode.PARAM_INVALID, msg);
    }

    /** 必填参数缺失。 */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    @ResponseStatus(HttpStatus.OK)
    public R<Void> handleMissingParam(MissingServletRequestParameterException e) {
        return R.fail(ErrorCode.PARAM_INVALID, "缺少必填参数: " + e.getParameterName());
    }

    /** 参数类型不匹配。 */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.OK)
    public R<Void> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return R.fail(ErrorCode.PARAM_INVALID, "参数类型错误: " + e.getName());
    }

    /** 非法参数。 */
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.OK)
    public R<Void> handleIllegalArgument(IllegalArgumentException e) {
        return R.fail(ErrorCode.PARAM_INVALID, e.getMessage());
    }

    /** 请求方式不支持。 */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    @ResponseStatus(HttpStatus.OK)
    public R<Void> handleMethod(HttpRequestMethodNotSupportedException e) {
        return R.fail(ErrorCode.PARAM_INVALID, "不支持的请求方式: " + e.getMethod());
    }

    /** 404。 */
    @ExceptionHandler(NoHandlerFoundException.class)
    @ResponseStatus(HttpStatus.OK)
    public R<Void> handleNotFound(NoHandlerFoundException e) {
        return R.fail(ErrorCode.DATA_NOT_FOUND, "接口不存在: " + e.getRequestURL());
    }

    /** 兜底：未预期的异常，打完整堆栈便于排查。 */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.OK)
    public R<Void> handleOther(Exception e, HttpServletRequest req) {
        log.error("[系统异常] {} {}", req.getMethod(), req.getRequestURI(), e);
        return R.fail(ErrorCode.SYSTEM_ERROR, "系统内部错误，请联系管理员");
    }
}
