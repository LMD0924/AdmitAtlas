package com.admitatlas.authuser;

import com.admitatlas.api.dto.AuthDTO;
import com.admitatlas.api.dto.ProfileDTO;
import com.admitatlas.common.BizException;
import com.admitatlas.common.ResultCode;
import com.admitatlas.common.auth.AuthContext;
import com.admitatlas.common.auth.AuthUser;
import com.admitatlas.common.auth.PasswordHasher;
import com.admitatlas.common.model.Models;
import com.admitatlas.common.repository.AdmitAtlasRepository;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * F-C-02 / F-S-03：个人中心。
 *
 * <p>所有查询都以 {@link AuthContext#require()} 的 userId 为边界，不接收前端传入的
 * user_id，因此无法读取他人档案。修改资料与修改密码都写审计；改密码复用
 * {@code updatePassword}（内部递增 token_version），使该账号所有已签发 JWT 立即失效。
 */
@Service
public class ProfileService {

    /** P1 单省试点年份，成绩档案按此年份取最近一条。 */
    private static final int PILOT_YEAR = 2026;
    private static final int MAX_DISPLAY_NAME = 32;
    private static final int MIN_PASSWORD = 8;
    private static final int MAX_PASSWORD = 64;

    private final AdmitAtlasRepository repo;

    public ProfileService(AdmitAtlasRepository repo) {
        this.repo = repo;
    }

    /** 身份信息 + 我的数据概览 + 最近成绩 / 方案 / 匹配任务。 */
    public Map<String, Object> detail() {
        AuthUser user = AuthContext.require();
        Models.UserAccount account = account(user.id());

        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("id", account.id());
        profile.put("username", account.username());
        profile.put("displayName", displayName(account));
        profile.put("role", account.role());
        profile.put("roleLabel", AuthUser.ROLE_ADMIN.equals(account.role()) ? "管理员" : "学生");
        profile.put("provinceCode", account.provinceCode());
        profile.put("provinceName", repo.province(account.provinceCode())
                .map(Models.Province::provinceName).orElse("未设置"));
        profile.put("accountStatus", account.status());
        profile.put("statusLabel", "ACTIVE".equals(account.status()) ? "正常" : "已停用");
        profile.put("createdAt", account.createdAt());
        profile.put("lastLoginAt", account.lastLoginAt());
        profile.put("tokenVersion", account.tokenVersion());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("profile", profile);
        data.put("stats", repo.userStats(user.id()));
        data.put("latestScore", repo.latestScore(user.id(), PILOT_YEAR).orElse(null));
        data.put("plans", repo.plans(user.id()).stream().limit(5).toList());
        data.put("tasks", repo.tasksByUser(user.id(), 5));
        data.put("dataVersion", repo.latestDataVersion());
        return data;
    }

    /** 自助修改展示名与生源地省份；角色、用户名、状态不可改。 */
    public AuthDTO.UserView update(ProfileDTO.ProfileRequest req) {
        AuthUser user = AuthContext.require();
        if (req == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "请求体不能为空");
        }
        String name = req.displayName() == null ? "" : req.displayName().trim();
        if (name.isBlank() || name.length() > MAX_DISPLAY_NAME) {
            throw new BizException(ResultCode.BAD_REQUEST,
                    "昵称不能为空且不得超过 " + MAX_DISPLAY_NAME + " 个字符");
        }
        String province = req.provinceCode() == null ? "" : req.provinceCode().trim();
        if (province.isBlank() || repo.province(province).isEmpty()) {
            throw new BizException(ResultCode.BAD_REQUEST, "生源地省份代码无效");
        }

        Models.UserAccount before = account(user.id());
        repo.updateUserProfile(user.id(), name, province);
        repo.writeAudit(user.id(), before.username(), "PROFILE_UPDATE", "USER",
                String.valueOf(user.id()),
                "昵称 " + displayName(before) + " → " + name
                        + "；省份 " + before.provinceCode() + " → " + province);
        return AuthService.toView(AuthUser.from(account(user.id())), false);
    }

    /** 校验原密码 → 更新哈希 → 递增 token_version → 前端需重新登录。 */
    public Map<String, Object> changePassword(ProfileDTO.PasswordRequest req) {
        AuthUser user = AuthContext.require();
        if (req == null || blank(req.oldPassword()) || blank(req.newPassword())) {
            throw new BizException(ResultCode.BAD_REQUEST, "原密码与新密码均不能为空");
        }
        String newPassword = req.newPassword();
        if (newPassword.length() < MIN_PASSWORD || newPassword.length() > MAX_PASSWORD) {
            throw new BizException(ResultCode.BAD_REQUEST, "新密码长度须为 8~64 位");
        }
        Models.UserAccount account = account(user.id());
        if (!PasswordHasher.matches(req.oldPassword(), account.passwordHash())) {
            throw new BizException(ResultCode.BAD_REQUEST, "原密码不正确");
        }
        if (PasswordHasher.matches(newPassword, account.passwordHash())) {
            throw new BizException(ResultCode.BAD_REQUEST, "新密码不能与原密码相同");
        }
        repo.updatePassword(user.id(), PasswordHasher.hash(newPassword));
        repo.writeAudit(user.id(), account.username(), "PASSWORD_CHANGE", "USER",
                String.valueOf(user.id()), "自助修改密码，已吊销全部已签发令牌");

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("changed", true);
        data.put("sessionRevoked", true);
        data.put("message", "密码已更新，请使用新密码重新登录");
        return data;
    }

    private Models.UserAccount account(long id) {
        return repo.userById(id).orElseThrow(() ->
                new BizException(ResultCode.NOT_FOUND, "账号不存在"));
    }

    private static String displayName(Models.UserAccount account) {
        return account.displayName() == null || account.displayName().isBlank()
                ? account.username() : account.displayName();
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
