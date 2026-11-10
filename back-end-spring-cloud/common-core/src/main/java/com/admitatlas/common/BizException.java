package com.admitatlas.common;

import java.util.Map;

/**
 * 业务异常，由全局异常处理器转换为统一响应。
 *
 * <p>可选携带 {@code data}，用于把「还剩几次机会 / 冻结到什么时候」这类
 * 结构化提示一并回给前端（统一响应恒 HTTP 200，业务码在 body.code）。
 */
public class BizException extends RuntimeException {

    private final int code;
    private final Map<String, Object> data;

    public BizException(ResultCode resultCode) {
        this(resultCode, resultCode.getMessage(), null);
    }

    public BizException(ResultCode resultCode, String message) {
        this(resultCode, message, null);
    }

    public BizException(ResultCode resultCode, String message, Map<String, Object> data) {
        super(message);
        this.code = resultCode.getCode();
        this.data = data;
    }

    public BizException(int code, String message) {
        this(code, message, null);
    }

    public BizException(int code, String message, Map<String, Object> data) {
        super(message);
        this.code = code;
        this.data = data;
    }

    public int getCode() {
        return code;
    }

    public Map<String, Object> getData() {
        return data;
    }
}
