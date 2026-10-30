package com.example.wms.common;

/**
 * 请求级登录上下文（ThreadLocal）。
 *
 * <p>由 {@code AuthInterceptor#preHandle} 写入、{@code afterCompletion} 清理。
 * Controller 里所有"操作人"字段一律从这里取，禁止再用请求参数传入，
 * 从根上消除冒名操作（SEC-18）。</p>
 */
public final class AuthContext {

    private static final ThreadLocal<LoginUser> HOLDER = new ThreadLocal<>();

    private AuthContext() {
    }

    public static void set(LoginUser user) {
        HOLDER.set(user);
    }

    public static void clear() {
        HOLDER.remove();
    }

    /** 可能为 null（例如登录接口本身） */
    public static LoginUser get() {
        return HOLDER.get();
    }

    /** 取当前登录人，取不到即抛错（受保护接口不应出现这种情况） */
    public static LoginUser require() {
        LoginUser u = HOLDER.get();
        if (u == null) {
            throw new IllegalStateException("登录状态已失效，请重新登录");
        }
        return u;
    }

    public static String employeeNo() {
        LoginUser u = HOLDER.get();
        return u == null ? null : u.employeeNo();
    }

    public static String employeeName() {
        LoginUser u = HOLDER.get();
        return u == null ? null : u.employeeName();
    }

    public static String role() {
        LoginUser u = HOLDER.get();
        return u == null ? null : u.role();
    }

    public static boolean hasRole(String... roles) {
        LoginUser u = HOLDER.get();
        return u != null && u.hasRole(roles);
    }
}
