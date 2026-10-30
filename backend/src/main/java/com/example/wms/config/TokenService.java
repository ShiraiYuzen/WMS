package com.example.wms.config;

import com.example.wms.common.LoginUser;
import com.example.wms.dto.LoginResponse;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 会话 Token 服务。
 *
 * <p>token 形如 Base64Url(employeeNo:uuid:expireAtMillis:role)，仅作为随机不可猜的凭证；
 * <b>服务端才是唯一可信来源</b>——所有身份信息（工号 / 姓名 / 角色）都存在内存会话表里，
 * 拦截器只认会话表，不解析 token 内容，因此不存在伪造或篡改 token 越权的可能。</p>
 *
 * <p>有效期 8 小时，过期自动清除。</p>
 */
@Component
public class TokenService {

    public static final long TOKEN_TTL_MILLIS = 8 * 60 * 60 * 1000L; // 8 小时

    /** 会话：token → 登录人 + 过期时间 */
    public record Session(LoginUser user, long expireAt) {
        boolean expired() {
            return System.currentTimeMillis() > expireAt;
        }
    }

    private final ConcurrentHashMap<String, Session> sessions = new ConcurrentHashMap<>();

    public LoginResponse issue(String employeeNo, String employeeName, String role, String team) {
        long loginTime = System.currentTimeMillis();
        long expireTime = loginTime + TOKEN_TTL_MILLIS;
        String r = role == null || role.isBlank() ? "PICKER" : role;
        String raw = employeeNo + ":" + UUID.randomUUID() + ":" + expireTime + ":" + r;
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
        sessions.put(token, new Session(new LoginUser(employeeNo, employeeName, r, team), expireTime));
        return new LoginResponse(token, employeeNo, employeeName, r, team);
    }

    /** 主动吊销（登出 / 改密后调用） */
    public void remove(String token) {
        if (token != null) {
            sessions.remove(token);
        }
    }

    /** 校验 token，返回服务端会话；无效或过期返回 null */
    public Session resolve(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        Session s = sessions.get(token);
        if (s == null) {
            return null;
        }
        if (s.expired()) {
            sessions.remove(token);
            return null;
        }
        return s;
    }
}
