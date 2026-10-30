package com.example.wms.dto;

/**
 * 登录响应：对齐 A 前端契约 { token, employeeNo, employeeName, role, team }。
 */
public class LoginResponse {
    private String token;
    private String employeeNo;
    private String employeeName;
    private String role;
    private String team;

    public LoginResponse(String token, String employeeNo, String employeeName, String role, String team) {
        this.token = token;
        this.employeeNo = employeeNo;
        this.employeeName = employeeName;
        this.role = role;
        this.team = team;
    }

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
    public String getEmployeeNo() { return employeeNo; }
    public void setEmployeeNo(String employeeNo) { this.employeeNo = employeeNo; }
    public String getEmployeeName() { return employeeName; }
    public void setEmployeeName(String employeeName) { this.employeeName = employeeName; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public String getTeam() { return team; }
    public void setTeam(String team) { this.team = team; }
}
