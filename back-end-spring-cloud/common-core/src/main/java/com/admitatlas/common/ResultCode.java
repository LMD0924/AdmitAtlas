package com.admitatlas.common;

/**
 * 全局错误码。业务模块可在此基础上扩展 1xxx 段以上的自定义码。
 */
public enum ResultCode {

    SUCCESS(0, "success"),
    BAD_REQUEST(400, "请求参数不合法"),
    UNAUTHORIZED(401, "未登录或登录已过期"),
    FORBIDDEN(403, "无权限访问"),
    NOT_FOUND(404, "资源不存在"),
    CONFLICT(409, "资源状态冲突"),
    NOT_IMPLEMENTED(501, "该能力尚未实现，将在后续阶段交付"),
    INTERNAL_ERROR(500, "服务内部错误"),
    UPSTREAM_ERROR(502, "依赖服务调用失败"),
    DATA_NOT_READY(503, "该省份 / 年份的录取数据尚未发布");

    private final int code;
    private final String message;

    ResultCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
