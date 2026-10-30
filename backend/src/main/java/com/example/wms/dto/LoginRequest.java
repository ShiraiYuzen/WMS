package com.example.wms.dto;

/**
 * 登录请求：密码为前端 btoa(Base64) 后的密文，与 sys_user.password 存储方式一致。
 */
public class LoginRequest {
    private String username;
    private String password;

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
}
