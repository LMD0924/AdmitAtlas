package com.admitatlas.common.auth;

import com.admitatlas.common.model.Models;

/** 当前请求的身份快照，只保留非敏感字段，绝不携带密码哈希。 */
public record AuthUser(long id, String username, String displayName,
                       String role, String provinceCode) {

    public static final String ROLE_STUDENT = "STUDENT";
    public static final String ROLE_ADMIN = "ADMIN";

    public boolean isAdmin() {
        return ROLE_ADMIN.equals(role);
    }

    public static AuthUser from(Models.UserAccount account) {
        return new AuthUser(account.id(), account.username(), account.displayName(),
                account.role(), account.provinceCode());
    }
}
