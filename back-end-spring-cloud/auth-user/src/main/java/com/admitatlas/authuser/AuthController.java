package com.admitatlas.authuser;

import com.admitatlas.api.dto.AuthDTO;
import com.admitatlas.common.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/** F-S-01/02：登录、注册、退出、当前身份。前三个公开，logout 需已登录。 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public Result<AuthDTO.LoginResponse> login(
            @RequestBody(required = false) AuthDTO.LoginRequest req) {
        return Result.ok(authService.login(req));
    }

    @PostMapping("/register")
    public Result<Map<String, Object>> register(
            @RequestBody(required = false) AuthDTO.RegisterRequest req) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("user", authService.register(req));
        data.put("next", "请使用刚注册的账号登录");
        return Result.ok(data);
    }

    @PostMapping("/logout")
    public Result<Map<String, Object>> logout() {
        authService.logout();
        return Result.ok(Map.of("loggedOut", true));
    }

    @GetMapping("/me")
    public Result<Map<String, Object>> me() {
        AuthDTO.UserView user = authService.current();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("user", user);
        data.put("guest", user.guest());
        data.put("role", user.role());
        data.put("loginRequired", false);   // 浏览公开信息不需要登录
        data.put("protectedFeatures", java.util.List.of(
                "student/scores", "match/analyze", "plans"));
        return Result.ok(data);
    }
}
