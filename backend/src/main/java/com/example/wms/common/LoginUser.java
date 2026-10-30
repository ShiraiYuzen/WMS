package com.example.wms.common;

/**
 * 当前登录人（由 token 解析出的服务端会话，不接受任何请求参数覆盖）。
 *
 * @param employeeNo   工号，用于写库的操作人字段
 * @param employeeName 姓名，用于日志展示
 * @param role         角色编码：ADMIN / WAVE_OPERATOR / PICKER / REVIEWER
 * @param team         班组
 */
public record LoginUser(String employeeNo, String employeeName, String role, String team) {

    public boolean hasRole(String... roles) {
        if (roles == null || roles.length == 0) return true;
        for (String r : roles) {
            if (r != null && r.equalsIgnoreCase(role)) return true;
        }
        return false;
    }
}
