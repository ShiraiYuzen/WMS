package com.example.wms.config;

import com.example.wms.dto.LoginResponse;
import com.example.wms.entity.User;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 简单 Token 服务：
 * token = Base64Url(username:uuid:expireAtMillis:role)，有效期 8 小时。
 * 校验时解码并检查过期时间，过期即失效。
 */
@Component
public class TokenService {

    public static final long TOKEN_TTL_MILLIS = 8 * 60 * 60 * 1000L; // 8 小时

    private final ConcurrentHashMap<String, Long> tokens = new ConcurrentHashMap<>();

    public LoginResponse issue(User user) {
        long loginTime = System.currentTimeMillis();
        long expireTime = loginTime + TOKEN_TTL_MILLIS;
        String role = user.getRole() == null || user.getRole().isBlank() ? "PICKER" : user.getRole();
        String raw = user.getUsername() + ":" + UUID.randomUUID() + ":" + expireTime + ":" + role;
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
        tokens.put(token, expireTime);
        return new LoginResponse(token, user.getUsername(), user.getNickname(), loginTime, expireTime, role);
    }

    public void remove(String token) {
        if (token != null) {
            tokens.remove(token);
        }
    }

    public String resolveRole(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        try {
            String raw = new String(Base64.getUrlDecoder().decode(token), StandardCharsets.UTF_8);
            String[] parts = raw.split(":");
            return parts.length >= 4 ? parts[3] : null;
        } catch (Exception e) {
            return null;
        }
    }

    public String validate(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        Long expireTime = tokens.get(token);
        if (expireTime == null) {
            return null;
        }
        if (System.currentTimeMillis() > expireTime) {
            tokens.remove(token);
            return null;
        }
        try {
            String raw = new String(Base64.getUrlDecoder().decode(token), StandardCharsets.UTF_8);
            return raw.split(":")[0];
        } catch (Exception e) {
            return null;
        }
    }
}
