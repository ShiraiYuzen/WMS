package com.example.wms.controller;

import com.example.wms.common.JdbcMaps;
import com.example.wms.common.PasswordHasher;
import com.example.wms.config.TokenService;
import com.example.wms.dto.ApiResult;
import com.example.wms.dto.LoginRequest;
import com.example.wms.dto.LoginResponse;
import com.example.wms.service.Repo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 登录鉴权。
 *
 * <p>口令校验走 {@link PasswordHasher}（PBKDF2-HMAC-SHA256，加盐慢哈希）。
 * 不再接受明文或 Base64 形式的口令存储——历史遗留行由
 * {@code DatabaseInitializer} 在启动时一次性迁移（SEC-02）。</p>
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final Repo repo;
    private final TokenService tokenService;

    public AuthController(Repo repo, TokenService tokenService) {
        this.repo = repo;
        this.tokenService = tokenService;
    }

    @PostMapping("/login")
    public ApiResult<LoginResponse> login(@RequestBody LoginRequest req) {
        if (req == null || req.getUsername() == null || req.getPassword() == null) {
            throw new IllegalArgumentException("请输入账号和密码");
        }
        Map<String, Object> u = JdbcMaps.one(repo.jdbc(),
                "SELECT u.*, e.team, e.employee_name FROM sys_user u " +
                        "LEFT JOIN employee e ON e.employee_no=u.employee_no WHERE u.username=?",
                req.getUsername().trim());

        // 账号不存在与口令错误返回同一文案，避免用户名枚举
        String stored = u == null ? null : Repo.str(u.get("password"));
        if (!PasswordHasher.matches(req.getPassword(), stored)) {
            throw new IllegalArgumentException("账号或密码错误");
        }

        // 老散列（PBKDF2）在这里无感升级为 BCrypt，用户不需要改密
        if (PasswordHasher.needsUpgrade(stored)) {
            int n = repo.jdbc().update("UPDATE sys_user SET password=? WHERE username=? AND password=?",
                    PasswordHasher.hash(req.getPassword()), req.getUsername().trim(), stored);
            if (n > 0) {
                log.info("账号 {} 的口令散列已升级为 BCrypt", req.getUsername().trim());
            }
        }

        String employeeNo = Repo.str(u.get("employeeNo"));
        String employeeName = Repo.str(u.get("employeeName"));
        if (employeeName == null) employeeName = Repo.str(u.get("nickname"));
        String role = Repo.str(u.get("role"));
        String team = Repo.str(u.get("team"));
        log.info("登录成功：{}（{}，{}）", employeeNo, employeeName, role);
        return ApiResult.ok(tokenService.issue(employeeNo, employeeName, role, team));
    }
}
