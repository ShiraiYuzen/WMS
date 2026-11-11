package com.example.wms.service;

import com.example.wms.common.PasswordHasher;
import com.example.wms.common.SqlSupport;
import com.example.wms.config.TokenService;
import com.example.wms.dto.LoginRequest;
import com.example.wms.dto.LoginResponse;
import com.example.wms.service.support.BizNumbers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final SqlSupport sql;
    private final TokenService tokenService;

    public AuthService(SqlSupport sql, TokenService tokenService) {
        this.sql = sql;
        this.tokenService = tokenService;
    }

    public LoginResponse login(LoginRequest req) {
        if (req == null || req.getUsername() == null || req.getPassword() == null) {
            throw new IllegalArgumentException("请输入账号和密码");
        }
        Map<String, Object> u = sql.one(
                "SELECT u.*, e.team, e.employee_name FROM sys_user u " +
                        "LEFT JOIN employee e ON e.employee_no=u.employee_no WHERE u.username=?",
                req.getUsername().trim());

        String stored = u == null ? null : BizNumbers.str(u.get("password"));
        if (!PasswordHasher.matches(req.getPassword(), stored)) {
            throw new IllegalArgumentException("账号或密码错误");
        }

        if (PasswordHasher.needsUpgrade(stored)) {
            int n = sql.update("UPDATE sys_user SET password=? WHERE username=? AND password=?",
                    PasswordHasher.hash(req.getPassword()), req.getUsername().trim(), stored);
            if (n > 0) {
                log.info("账号 {} 的口令散列已升级为 BCrypt", req.getUsername().trim());
            }
        }

        String employeeNo = BizNumbers.str(u.get("employeeNo"));
        String employeeName = BizNumbers.str(u.get("employeeName"));
        if (employeeName == null) employeeName = BizNumbers.str(u.get("nickname"));
        String role = BizNumbers.str(u.get("role"));
        String team = BizNumbers.str(u.get("team"));
        log.info("登录成功：{}（{}，{}）", employeeNo, employeeName, role);
        return tokenService.issue(employeeNo, employeeName, role, team);
    }
}
