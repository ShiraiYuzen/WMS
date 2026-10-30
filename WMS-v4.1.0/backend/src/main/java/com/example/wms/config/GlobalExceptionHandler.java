package com.example.wms.config;

import com.example.wms.common.ForbiddenException;
import com.example.wms.dto.ApiResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 全局异常处理。
 *
 * <p>两层语义，不要混：</p>
 * <ul>
 *   <li><b>业务异常</b>（参数校验、状态机冲突）：HTTP 200 + {@code code=1}，
 *       前端按 message 直接提示，这是约定好的交互方式；</li>
 *   <li><b>协议异常</b>（路由不存在、方法不支持、服务端错误）：必须给出真实的 HTTP 状态码，
 *       否则监控、网关、前端拦截器都没法按状态码分流（SEC-11）。</li>
 * </ul>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(IllegalArgumentException.class)
    public ApiResult<Void> handleIllegalArgument(IllegalArgumentException e) {
        log.warn("业务校验失败: {}", e.getMessage());
        return ApiResult.fail(e.getMessage());
    }

    @ExceptionHandler(ForbiddenException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ApiResult<Void> handleForbidden(ForbiddenException e) {
        log.warn("越权访问被拒绝: {}", e.getMessage());
        return ApiResult.fail(e.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    public ApiResult<Void> handleIllegalState(IllegalStateException e) {
        log.warn("业务状态异常: {}", e.getMessage());
        return ApiResult.fail(e.getMessage());
    }

    /**
     * 路由不存在 → 404。
     *
     * <p>Spring 6 之后未匹配的路径会抛 {@link NoResourceFoundException}（静态资源兜底），
     * 以前被下面的兜底分支接住、回成了 200。这里单独处理，并且只回固定文案，
     * 不把「No static resource api/xxx」这种内部细节吐出去。</p>
     */
    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiResult<Void> handleNotFound(Exception e) {
        log.warn("请求的路径不存在: {}", e.getMessage());
        return ApiResult.fail("接口不存在");
    }

    /** 路径对但方法不对（例如对 /api/auth/login 发 GET）→ 405 */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    @ResponseStatus(HttpStatus.METHOD_NOT_ALLOWED)
    public ApiResult<Void> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        log.warn("请求方法不支持: {}", e.getMessage());
        return ApiResult.fail("不支持的请求方法：" + e.getMethod());
    }

    /**
     * 请求参数不合法 → 400。
     *
     * <p>参数类型错、必填参数缺失、请求体不是合法 JSON，都是调用方的问题，
     * 不该混进 500（服务端错误会误触告警），也不该像以前那样回 200。</p>
     */
    @ExceptionHandler({MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class,
            HttpMessageNotReadableException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResult<Void> handleBadRequest(Exception e) {
        log.warn("请求参数不合法: {}", e.getMessage());
        return ApiResult.fail("请求参数不合法，请检查后重试");
    }

    /**
     * 兜底 → 500。
     *
     * <p>以前这里也回 200，等于把所有未预期错误伪装成成功。现在给 500，
     * 并且响应体只给固定文案，堆栈只进日志（避免 SQL 片段之类的内部信息外泄）。</p>
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResult<Void> handleException(Exception e) {
        log.error("系统异常", e);
        return ApiResult.fail("系统异常，请稍后重试或联系管理员");
    }
}
