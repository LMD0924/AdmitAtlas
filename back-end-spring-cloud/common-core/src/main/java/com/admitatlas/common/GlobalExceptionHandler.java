package com.admitatlas.common;

import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理。由 launcher 的唯一启动类扫描 com.admitatlas 根包时统一装配。
 *
 * <p>兜底处理器只捕获 RuntimeException：Spring MVC 的 NoHandlerFoundException /
 * NoResourceFoundException 是 checked ServletException，交给 Spring 默认的异常解析器
 * 处理成真正的 404，避免「404 被全局兜底包成 500」。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    public Result<java.util.Map<String, Object>> handleBizException(BizException ex) {
        return new Result<>(ex.getCode(), ex.getMessage(), ex.getData());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public Result<Void> handleIllegalArgument(IllegalArgumentException ex) {
        return Result.fail(ResultCode.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(RuntimeException.class)
    public Result<Void> handleUnexpected(RuntimeException ex) {
        return Result.fail(ResultCode.INTERNAL_ERROR, ex.getMessage());
    }
}
