package com.example.wms.dto;

public class LoginResponse {
    private String token;
    private String username;
    private String nickname;
    private long loginTime;
    private long expireTime;
    private String role;

    public LoginResponse(String token, String username, String nickname, long loginTime, long expireTime, String role) {
        this.token = token;
        this.username = username;
        this.nickname = nickname;
        this.loginTime = loginTime;
        this.expireTime = expireTime;
        this.role = role;
    }

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }
    public long getLoginTime() { return loginTime; }
    public void setLoginTime(long loginTime) { this.loginTime = loginTime; }
    public long getExpireTime() { return expireTime; }
    public void setExpireTime(long expireTime) { this.expireTime = expireTime; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
}
