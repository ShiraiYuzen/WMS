package com.example.wms.controller;

import com.example.wms.dto.ApiResult;
import com.example.wms.dto.LoginRequest;
import com.example.wms.dto.LoginResponse;
import com.example.wms.entity.User;
import com.example.wms.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ApiResult<LoginResponse> login(@RequestBody LoginRequest req) {
        return ApiResult.ok(authService.login(req));
    }

    @PostMapping("/logout")
    public ApiResult<Void> logout(HttpServletRequest request) {
        String auth = request.getHeader("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            authService.logout(auth.substring("Bearer ".length()).trim());
        }
        return ApiResult.ok();
    }

    @GetMapping("/me")
    public ApiResult<User> me(@RequestAttribute("loginUser") String username) {
        return ApiResult.ok(authService.me(username));
    }
}
