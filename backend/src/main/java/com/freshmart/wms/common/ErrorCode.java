package com.freshmart.wms.common;

import lombok.Getter;

/**
 * 业务错误码字典。
 *
 * <p>编码分段约定：
 * <ul>
 *   <li>0         —— 成功</li>
 *   <li>1xxxx     —— 通用/参数类</li>
 *   <li>2xxxx     —— 订单域</li>
 *   <li>3xxxx     —— 波次域</li>
 *   <li>4xxxx     —— 拣货域</li>
 *   <li>5xxxx     —— 复核/打包域</li>
 *   <li>6xxxx     —— 出库交接域</li>
 *   <li>7xxxx     —— 缺货处理域</li>
 *   <li>9xxxx     —— 系统类</li>
 * </ul>
 */
@Getter
public enum ErrorCode {

    /* ---------------- 通用 ---------------- */
    PARAM_INVALID(10001, "请求参数不合法"),
    DATA_NOT_FOUND(10002, "数据不存在"),
    DATA_ALREADY_EXISTS(10003, "数据已存在"),
    OPERATION_NOT_ALLOWED(10004, "当前状态不允许该操作"),
    NO_PERMISSION(10005, "无操作权限"),

    /* ---------------- 订单域 ---------------- */
    ORDER_NOT_FOUND(20001, "订单不存在"),
    ORDER_STATUS_ILLEGAL(20002, "订单状态不满足操作条件"),
    ORDER_ALREADY_IN_WAVE(20003, "订单已归属其他波次"),
    ORDER_POOL_EMPTY(20004, "所选订单中无可汇聚的订单"),

    /* ---------------- 波次域 ---------------- */
    WAVE_NOT_FOUND(30001, "波次不存在"),
    WAVE_STATUS_ILLEGAL(30002, "波次状态不满足操作条件"),
    WAVE_NO_CANDIDATE_ORDER(30003, "没有符合条件的订单，无法生成波次"),
    WAVE_OVER_CAPACITY(30004, "波次容量超出上限"),
    WAVE_PICKER_REQUIRED(30005, "请先为波次分配拣货员"),
    WAVE_NOT_FINISHED(30006, "波次尚未拣货完成，不能进入复核"),

    /* ---------------- 拣货域 ---------------- */
    PICK_TASK_NOT_FOUND(40001, "拣货任务不存在"),
    PICK_TASK_STATUS_ILLEGAL(40002, "拣货任务状态不满足操作条件"),
    PICK_QTY_ILLEGAL(40003, "实拣数量不合法"),
    PICK_NOT_OWNER(40004, "该拣货任务不属于当前拣货员"),
    PICK_TASK_NOT_FINISHED(40005, "拣货任务尚未完成"),

    /* ---------------- 复核域 ---------------- */
    REVIEW_NOT_FOUND(50001, "复核记录不存在"),
    REVIEW_STATUS_ILLEGAL(50002, "复核记录状态不满足操作条件"),
    REVIEW_DIFF_QTY_ILLEGAL(50003, "差异数量不合法"),

    /* ---------------- 出库域 ---------------- */
    HANDOVER_NOT_FOUND(60001, "交接单不存在"),
    HANDOVER_STATUS_ILLEGAL(60002, "交接单状态不满足操作条件"),
    HANDOVER_WAVE_NOT_READY(60003, "波次未完成复核，不能发起交接"),

    /* ---------------- 缺货域 ---------------- */
    SHORTAGE_NOT_FOUND(70001, "缺货记录不存在"),
    SHORTAGE_ALREADY_HANDLED(70002, "缺货记录已处理完毕"),
    SHORTAGE_HANDLE_TYPE_INVALID(70003, "缺货处理方式不合法"),

    /* ---------------- 系统 ---------------- */
    SYSTEM_ERROR(90000, "系统内部错误");

    private final Integer code;
    private final String message;

    ErrorCode(Integer code, String message) {
        this.code = code;
        this.message = message;
    }
}
