package com.example.wms.config;

import com.example.wms.common.AuthContext;
import com.example.wms.common.LoginUser;
import com.example.wms.common.RequireRole;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * 鉴权 + 授权拦截器。
 *
 * <p>三件事：</p>
 * <ol>
 *   <li>校验 Authorization: Bearer &lt;token&gt; 是否在服务端会话表中（未登录 401）；</li>
 *   <li>把登录人写入 {@link AuthContext}，供 Controller 取操作人（SEC-18）；</li>
 *   <li>读取 {@link RequireRole} 注解做角色校验，不匹配返回 403（SEC-01）。</li>
 * </ol>
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    private final TokenService tokenService;
    /** Spring Boot 4 自带 Jackson 3，这里直接复用，不再单独引 Jackson 2 */
    private static final ObjectMapper JSON = JsonMapper.builder().build();

    public AuthInterceptor(TokenService tokenService) {
        this.tokenService = tokenService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String auth = request.getHeader("Authorization");
        if (auth == null || !auth.startsWith("Bearer ")) {
            writeError(response, HttpServletResponse.SC_UNAUTHORIZED, 401, "未登录或缺少 Authorization 头");
            return false;
        }
        String token = auth.substring("Bearer ".length()).trim();
        TokenService.Session session = tokenService.resolve(token);
        if (session == null) {
            writeError(response, HttpServletResponse.SC_UNAUTHORIZED, 401, "登录已失效，请重新登录");
            return false;
        }

        LoginUser user = session.user();
        AuthContext.set(user);

        if (!hasPermission(handler, user)) {
            AuthContext.clear();
            writeError(response, HttpServletResponse.SC_FORBIDDEN, 403,
                    "当前角色（" + roleText(user.role()) + "）无权执行该操作");
            return false;
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        // 线程池复用线程，必须清理，否则会串号
        AuthContext.clear();
    }

    /** 方法上的注解优先，其次看 Controller 类上的注解；都没有则"登录即可访问" */
    private boolean hasPermission(Object handler, LoginUser user) {
        if (!(handler instanceof HandlerMethod hm)) {
            return true;
        }
        RequireRole rr = hm.getMethodAnnotation(RequireRole.class);
        if (rr == null) {
            rr = hm.getBeanType().getAnnotation(RequireRole.class);
        }
        return rr == null || user.hasRole(rr.value());
    }

    private static String roleText(String role) {
        return switch (role == null ? "" : role) {
            case "ADMIN" -> "仓库主管";
            case "WAVE_OPERATOR" -> "波次员";
            case "PICKER" -> "拣货员";
            case "REVIEWER" -> "复核员";
            default -> role == null ? "未知" : role;
        };
    }

    private void writeError(HttpServletResponse response, int httpStatus, int code, String message) throws Exception {
        response.setStatus(httpStatus);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        Map<String, Object> body = new HashMap<>();
        body.put("code", code);
        body.put("message", message);
        body.put("data", null);
        body.put("timestamp", System.currentTimeMillis());
        response.getWriter().write(JSON.writeValueAsString(body));
    }
}
