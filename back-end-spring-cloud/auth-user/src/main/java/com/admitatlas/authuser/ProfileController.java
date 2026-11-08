package com.admitatlas.authuser;

import com.admitatlas.api.dto.AuthDTO;
import com.admitatlas.api.dto.ProfileDTO;
import com.admitatlas.common.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * F-C-02：个人中心接口。三条路径均在 AuthInterceptor 登记为 AUTHENTICATED，
 * 游客访问返回 401；响应按当前登录用户隔离，不接受前端指定 userId。
 */
@RestController
@RequestMapping("/api/v1/users")
public class ProfileController {

    private final ProfileService profileService;

    public ProfileController(ProfileService profileService) {
        this.profileService = profileService;
    }

    @GetMapping("/profile")
    public Result<Map<String, Object>> profile() {
        return Result.ok(profileService.detail());
    }

    /** 返回更新后的 UserView，前端据此同步导航栏与本地会话。 */
    @PutMapping("/profile")
    public Result<AuthDTO.UserView> updateProfile(
            @RequestBody(required = false) ProfileDTO.ProfileRequest req) {
        return Result.ok(profileService.update(req));
    }

    @PostMapping("/password")
    public Result<Map<String, Object>> changePassword(
            @RequestBody(required = false) ProfileDTO.PasswordRequest req) {
        return Result.ok(profileService.changePassword(req));
    }
}
