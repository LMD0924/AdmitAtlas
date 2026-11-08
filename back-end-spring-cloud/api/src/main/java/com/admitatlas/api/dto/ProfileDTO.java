package com.admitatlas.api.dto;

/**
 * F-C-02 / F-S-03：个人中心契约。
 *
 * <p>只允许修改展示名与生源地省份；角色、用户名、账号状态由管理员维护，
 * 不在此接口暴露，防止自助提权。密码修改必须校验原密码，成功后吊销全部会话。
 */
public final class ProfileDTO {

    private ProfileDTO() {
    }

    public record ProfileRequest(String displayName, String provinceCode) {
    }

    public record PasswordRequest(String oldPassword, String newPassword) {
    }
}
