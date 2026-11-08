package com.admitatlas.api.dto;

/** F-S-01/02：双身份登录契约。注册接口强制 STUDENT，不允许自助创建管理员。 */
public final class AuthDTO {

    private AuthDTO() {
    }

    public record LoginRequest(String username, String password) {
    }

    public record RegisterRequest(String username, String password,
                                  String displayName, String provinceCode) {
    }

    public record UserView(long id, String username, String displayName, String role,
                           String provinceCode, boolean admin, boolean guest) {
    }

    public record LoginResponse(String token, long expiresIn, String tokenType,
                                UserView user) {
    }
}
