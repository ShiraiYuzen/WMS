package com.example.wms.service;

import com.example.wms.config.TokenService;
import com.example.wms.dao.UserDao;
import com.example.wms.dto.LoginRequest;
import com.example.wms.dto.LoginResponse;
import com.example.wms.entity.User;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Service
public class AuthService {

    private final UserDao userDao;
    private final TokenService tokenService;

    public AuthService(UserDao userDao, TokenService tokenService) {
        this.userDao = userDao;
        this.tokenService = tokenService;
    }

    public LoginResponse login(LoginRequest req) {
        if (req == null || req.getUsername() == null || req.getUsername().isBlank()) {
            throw new IllegalArgumentException("用户名不能为空");
        }
        User user = userDao.findByUsername(req.getUsername().trim());
        if (user == null) {
            throw new IllegalArgumentException("用户名或密码错误");
        }
        if ("停用".equals(user.getStatus())) {
            throw new IllegalArgumentException("账号已停用，请联系管理员");
        }
        String rawPassword = decode(req.getPassword());
        if (!rawPassword.equals(user.getPassword())) {
            throw new IllegalArgumentException("用户名或密码错误");
        }
        return tokenService.issue(user);
    }

    public void logout(String token) {
        tokenService.remove(token);
    }

    public User me(String username) {
        User user = userDao.findByUsername(username);
        if (user != null) {
            user.setPassword(null);
        }
        return user;
    }

    private String decode(String password) {
        if (password == null) {
            return "";
        }
        try {
            return new String(Base64.getDecoder().decode(password), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return password;
        }
    }
}
