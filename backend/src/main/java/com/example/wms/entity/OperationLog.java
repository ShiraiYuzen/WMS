package com.example.wms.entity;

/** 操作日志（第三版新增，融合自 A 版的操作日志留痕） */
public class OperationLog {
    private Long id;
    private String module;
    private String action;
    private String method;
    private String uri;
    private String operator;
    private String role;
    private String detail;
    private Long costMs;
    private String result;
    private String createTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getModule() { return module; }
    public void setModule(String module) { this.module = module; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public String getMethod() { return method; }
    public void setMethod(String method) { this.method = method; }
    public String getUri() { return uri; }
    public void setUri(String uri) { this.uri = uri; }
    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public String getDetail() { return detail; }
    public void setDetail(String detail) { this.detail = detail; }
    public Long getCostMs() { return costMs; }
    public void setCostMs(Long costMs) { this.costMs = costMs; }
    public String getResult() { return result; }
    public void setResult(String result) { this.result = result; }
    public String getCreateTime() { return createTime; }
    public void setCreateTime(String createTime) { this.createTime = createTime; }
}
