package com.example.wms.controller;

import com.example.wms.dto.ApiResult;
import com.example.wms.dto.PageResult;
import com.example.wms.entity.User;
import com.example.wms.service.UserService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/user")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/list")
    public ApiResult<PageResult<User>> list(@RequestParam(required = false) String keyword,
                                            @RequestParam(required = false) String role,
                                            @RequestParam(defaultValue = "1") int page,
                                            @RequestParam(defaultValue = "10") int size) {
        return ApiResult.ok(userService.list(keyword, role, page, size));
    }

    @GetMapping("/all")
    public ApiResult<List<User>> all() {
        return ApiResult.ok(userService.listAll());
    }

    @GetMapping("/role")
    public ApiResult<List<User>> byRole(@RequestParam String role) {
        return ApiResult.ok(userService.listByRole(role));
    }

    @PostMapping("/add")
    public ApiResult<User> add(@RequestBody User user) {
        return ApiResult.ok(userService.add(user));
    }

    @PostMapping("/update")
    public ApiResult<Void> update(@RequestBody User user) {
        userService.update(user);
        return ApiResult.ok();
    }

    @PostMapping("/resetPassword")
    public ApiResult<Void> resetPassword(@RequestBody Map<String, Object> body) {
        Object id = body.get("id");
        Object pwd = body.get("password");
        userService.resetPassword(id == null ? null : Long.valueOf(id.toString()),
                pwd == null ? null : pwd.toString());
        return ApiResult.ok();
    }

    @PostMapping("/delete")
    public ApiResult<Void> delete(@RequestBody Map<String, Object> body) {
        Object id = body.get("id");
        userService.delete(id == null ? null : Long.valueOf(id.toString()));
        return ApiResult.ok();
    }
}
