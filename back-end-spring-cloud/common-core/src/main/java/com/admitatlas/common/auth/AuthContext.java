package com.admitatlas.common.auth;

import com.admitatlas.common.BizException;
import com.admitatlas.common.ResultCode;

/**
 * 请求级身份上下文。由 AuthInterceptor 在 preHandle 写入、afterCompletion 清理，
 * 业务服务统一通过 {@link #userId()} 取当前用户，替代 P1 写死的 DEMO_USER_ID。
 */
public final class AuthContext {

    private static final ThreadLocal<AuthUser> CURRENT = new ThreadLocal<>();

    private AuthContext() {
    }

    public static void set(AuthUser user) {
        CURRENT.set(user);
    }

    /** 游客访问公开接口时可能为 null。 */
    public static AuthUser get() {
        return CURRENT.get();
    }

    public static AuthUser require() {
        AuthUser user = CURRENT.get();
        if (user == null) {
            throw new BizException(ResultCode.UNAUTHORIZED, "请先登录后再使用该功能");
        }
        return user;
    }

    public static long userId() {
        return require().id();
    }

    public static boolean isAdmin() {
        AuthUser user = CURRENT.get();
        return user != null && user.isAdmin();
    }

    public static void clear() {
        CURRENT.remove();
    }
}
