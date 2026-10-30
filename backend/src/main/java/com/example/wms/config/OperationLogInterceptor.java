package com.example.wms.config;

import com.example.wms.entity.OperationLog;
import com.example.wms.service.OperationLogService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Map;

/**
 * 操作日志拦截器（第三版新增，融合自 A 版的操作留痕能力）。
 * 对非 GET 请求在完成后异步记录一条操作日志。
 */
@Component
public class OperationLogInterceptor implements HandlerInterceptor {

    private static final Map<String, String> MODULE = Map.ofEntries(
            Map.entry("wave", "波次管理"),
            Map.entry("order", "订单汇聚"),
            Map.entry("pick", "拣货执行"),
            Map.entry("review", "复核打包"),
            Map.entry("outbound", "出库交接"),
            Map.entry("shortage", "缺货处理"),
            Map.entry("sku", "SKU管理"),
            Map.entry("inventory", "库存"),
            Map.entry("user", "用户管理"),
            Map.entry("base", "基础数据"),
            Map.entry("auth", "鉴权")
    );

    private static final Map<String, String> ACTION = Map.ofEntries(
            Map.entry("create", "生成波次"),
            Map.entry("preview", "波次预览"),
            Map.entry("assign", "指派拣货员"),
            Map.entry("cancel", "取消"),
            Map.entry("add", "新增"),
            Map.entry("update", "修改"),
            Map.entry("delete", "删除"),
            Map.entry("save", "保存"),
            Map.entry("start", "开始拣货"),
            Map.entry("submit", "提交"),
            Map.entry("finish", "完成"),
            Map.entry("handle", "处理"),
            Map.entry("resetPassword", "重置密码"),
            Map.entry("login", "登录"),
            Map.entry("logout", "退出")
    );

    private final OperationLogService logService;

    public OperationLogInterceptor(OperationLogService logService) {
        this.logService = logService;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        String method = request.getMethod();
        if ("GET".equalsIgnoreCase(method) || "OPTIONS".equalsIgnoreCase(method)) {
            return;
        }
        try {
            String uri = request.getRequestURI();
            String[] parts = uri.split("/");
            String seg = parts.length > 2 ? parts[2] : "";
            String last = parts.length > 0 ? parts[parts.length - 1] : "";

            OperationLog log = new OperationLog();
            log.setModule(MODULE.getOrDefault(seg, seg));
            log.setAction(ACTION.getOrDefault(last, last));
            log.setMethod(method);
            log.setUri(uri);
            Object user = request.getAttribute("loginUser");
            Object role = request.getAttribute("loginRole");
            log.setOperator(user == null ? "-" : user.toString());
            log.setRole(role == null ? "-" : role.toString());
            log.setDetail(request.getQueryString());
            log.setCostMs(0L);
            log.setResult(ex == null && response.getStatus() < 400 ? "成功" : "失败");
            logService.record(log);
        } catch (Exception ignore) {
            // 日志记录失败不影响主流程
        }
    }
}
