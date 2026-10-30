package com.example.wms.common;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 接口角色白名单（SEC-01）。
 *
 * <p>标在方法或 Controller 类上，由 {@code AuthInterceptor} 统一校验；
 * 不标的方法默认"登录即可访问"（只读接口的通用策略）。</p>
 *
 * <pre>
 * &#64;RequireRole({"ADMIN", "WAVE_OPERATOR"})
 * &#64;PostMapping("/generate")
 * </pre>
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireRole {

    /** 允许访问的角色编码，任一匹配即通过 */
    String[] value();
}
