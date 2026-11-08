package com.admitatlas.common.auth;

import com.admitatlas.common.BizException;
import com.admitatlas.common.ResultCode;
import com.admitatlas.common.model.Models;
import com.admitatlas.common.repository.AdmitAtlasRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 三级路径权限：游客 / 学生 / 管理员，以及 token 失效与 CORS 预检。 */
class AuthInterceptorTest {

    private static final String SECRET = "admitatlas-test-secret-please-change-2026";

    private final JwtUtil jwt = new JwtUtil(SECRET, 3600);
    private AdmitAtlasRepository repo;
    private AuthInterceptor interceptor;

    private static Models.UserAccount account(long id, String role, int version) {
        return new Models.UserAccount(id, role.equals("ADMIN") ? "admin" : "student",
                "hash", "演示账号", "42", role, "ACTIVE", version, null, null);
    }

    private final Models.UserAccount student = account(7L, "STUDENT", 3);
    private final Models.UserAccount admin = account(8L, "ADMIN", 1);

    @BeforeEach
    void setUp() {
        AuthContext.clear();
        repo = mock(AdmitAtlasRepository.class);
        when(repo.userById(7L)).thenReturn(Optional.of(student));
        when(repo.userById(8L)).thenReturn(Optional.of(admin));
        interceptor = new AuthInterceptor(jwt, repo);
    }

    @AfterEach
    void tearDown() {
        AuthContext.clear();
    }

    private void handle(String uri, String token) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        if (token != null) {
            request.addHeader("Authorization", "Bearer " + token);
        }
        interceptor.preHandle(request, new MockHttpServletResponse(), new Object());
    }

    private String studentToken() {
        return jwt.issue(student);
    }

    private String adminToken() {
        return jwt.issue(admin);
    }

    @Test
    @DisplayName("游客访问公开接口放行且不写入身份")
    void publicAsGuest() {
        assertDoesNotThrow(() -> handle("/api/v1/schools", null));
        assertNull(AuthContext.get());
    }

    @Test
    @DisplayName("游客访问成绩 / 方案 / 匹配返回 401")
    void protectedNeedsLogin() {
        for (String uri : new String[]{"/api/v1/student/scores", "/api/v1/plans",
                "/api/v1/match/analyze", "/api/v1/auth/logout",
                "/api/v1/users/profile", "/api/v1/users/password"}) {
            AuthContext.clear();
            BizException ex = assertThrows(BizException.class, () -> handle(uri, null), uri);
            assertEquals(ResultCode.UNAUTHORIZED.getCode(), ex.getCode());
        }
    }

    @Test
    @DisplayName("学生访问自己的受保护接口放行并写入身份")
    void studentCanUseProtected() {
        assertDoesNotThrow(() -> handle("/api/v1/plans", studentToken()));
        assertNotNull(AuthContext.get());
        assertEquals(7L, AuthContext.userId());
        assertFalse(AuthContext.isAdmin());
    }

    @Test
    @DisplayName("学生访问管理端返回 403")
    void studentCannotUseAdmin() {
        BizException ex = assertThrows(BizException.class,
                () -> handle("/api/v1/admin/overview", studentToken()));
        assertEquals(ResultCode.FORBIDDEN.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("管理员访问管理端放行")
    void adminCanUseAdmin() {
        assertDoesNotThrow(() -> handle("/api/v1/admin/users", adminToken()));
        assertTrue(AuthContext.isAdmin());
        assertEquals(8L, AuthContext.userId());
    }

    @Test
    @DisplayName("个人中心需登录，但 /users/me 仍对游客开放")
    void profileProtectedButMePublic() {
        assertDoesNotThrow(() -> handle("/api/v1/users/me", null));
        assertNull(AuthContext.get());
        AuthContext.clear();
        BizException ex = assertThrows(BizException.class,
                () -> handle("/api/v1/users/profile", null));
        assertEquals(ResultCode.UNAUTHORIZED.getCode(), ex.getCode());
        AuthContext.clear();
        assertDoesNotThrow(() -> handle("/api/v1/users/profile", studentToken()));
        assertEquals(7L, AuthContext.userId());
    }

    @Test
    @DisplayName("无效 token 视为游客，受保护接口返回 401")
    void invalidTokenTreatedAsGuest() {
        BizException ex = assertThrows(BizException.class,
                () -> handle("/api/v1/plans", "not-a-jwt"));
        assertEquals(ResultCode.UNAUTHORIZED.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("账号停用或 token_version 变化后旧 token 立即失效")
    void staleAccountInvalidatesToken() {
        when(repo.userById(7L)).thenReturn(Optional.of(
                account(7L, "STUDENT", 4)));   // 版本号已 +1
        BizException ex = assertThrows(BizException.class,
                () -> handle("/api/v1/plans", studentToken()));
        assertEquals(ResultCode.UNAUTHORIZED.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("OPTIONS 预检直接放行，不校验 token")
    void optionsBypasses() {
        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/api/v1/admin/users");
        assertDoesNotThrow(() -> interceptor.preHandle(request,
                new MockHttpServletResponse(), new Object()));
    }

    @Test
    @DisplayName("未知路径保持公开语义，不会被鉴权层改成 401")
    void unknownPathStaysPublic() {
        assertDoesNotThrow(() -> handle("/api/v1/does-not-exist", null));
    }

    @Test
    @DisplayName("afterCompletion 清理 ThreadLocal，避免线程复用串号")
    void contextCleared() {
        handle("/api/v1/plans", studentToken());
        interceptor.afterCompletion(new MockHttpServletRequest(),
                new MockHttpServletResponse(), new Object(), null);
        assertNull(AuthContext.get());
    }
}
