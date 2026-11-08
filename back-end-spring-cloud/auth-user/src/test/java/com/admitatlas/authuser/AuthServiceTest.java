package com.admitatlas.authuser;

import com.admitatlas.api.dto.AuthDTO;
import com.admitatlas.common.BizException;
import com.admitatlas.common.ResultCode;
import com.admitatlas.common.auth.AuthContext;
import com.admitatlas.common.auth.AuthUser;
import com.admitatlas.common.auth.JwtUtil;
import com.admitatlas.common.auth.PasswordHasher;
import com.admitatlas.common.model.Models;
import com.admitatlas.common.repository.AdmitAtlasRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 登录、注册、退出与角色防越权。 */
class AuthServiceTest {

    private static final String SECRET = "admitatlas-test-secret-please-change-2026";

    private AdmitAtlasRepository repo;
    private AuthService service;

    private static Models.UserAccount account(long id, String username, String role) {
        return new Models.UserAccount(id, username, PasswordHasher.hash("Demo@123456"),
                "演示学生", "42", role, "ACTIVE", 2, null, null);
    }

    @BeforeEach
    void setUp() {
        AuthContext.clear();
        repo = mock(AdmitAtlasRepository.class);
        service = new AuthService(repo, new JwtUtil(SECRET, 3600));
    }

    @AfterEach
    void tearDown() {
        AuthContext.clear();
    }

    @Test
    @DisplayName("账号密码正确时签发 token 并记录登录时间")
    void loginSuccess() {
        Models.UserAccount a = account(7L, "student", "STUDENT");
        when(repo.userByUsername("student")).thenReturn(Optional.of(a));

        AuthDTO.LoginResponse res = service.login(
                new AuthDTO.LoginRequest("student", "Demo@123456"));

        assertNotNull(res.token());
        assertEquals("Bearer", res.tokenType());
        assertEquals("STUDENT", res.user().role());
        assertFalse(res.user().admin());
        assertFalse(res.user().guest());
        verify(repo).touchLogin(7L);
    }

    @Test
    @DisplayName("密码错误返回 401，且不泄露账号是否存在")
    void loginWrongPassword() {
        when(repo.userByUsername("student")).thenReturn(Optional.of(
                account(7L, "student", "STUDENT")));
        BizException ex = assertThrows(BizException.class, () -> service.login(
                new AuthDTO.LoginRequest("student", "Wrong@12345")));
        assertEquals(ResultCode.UNAUTHORIZED.getCode(), ex.getCode());
        assertEquals("用户名或密码错误", ex.getMessage());
        verify(repo, never()).touchLogin(anyLong());
    }

    @Test
    @DisplayName("停用账号返回 403")
    void loginDisabled() {
        Models.UserAccount a = account(7L, "student", "STUDENT");
        Models.UserAccount disabled = new Models.UserAccount(a.id(), a.username(),
                a.passwordHash(), a.displayName(), a.provinceCode(), a.role(),
                "DISABLED", a.tokenVersion(), null, null);
        when(repo.userByUsername("student")).thenReturn(Optional.of(disabled));
        BizException ex = assertThrows(BizException.class, () -> service.login(
                new AuthDTO.LoginRequest("student", "Demo@123456")));
        assertEquals(ResultCode.FORBIDDEN.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("自助注册强制 STUDENT，不允许指定 ADMIN")
    void registerForcesStudentRole() {
        when(repo.userByUsername("newbie")).thenReturn(Optional.empty());
        when(repo.createUser(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(9L);
        when(repo.userById(9L)).thenReturn(Optional.of(account(9L, "newbie", "STUDENT")));

        AuthDTO.UserView view = service.register(new AuthDTO.RegisterRequest(
                "newbie", "Password@1", "新同学", "42"));

        assertEquals("STUDENT", view.role());
        verify(repo).createUser(eq("newbie"), anyString(), eq("新同学"), eq("42"),
                eq(AuthUser.ROLE_STUDENT));
        verify(repo).writeAudit(eq(9L), eq("newbie"), eq("REGISTER"),
                eq("USER"), eq("9"), anyString());
    }

    @Test
    @DisplayName("用户名重复返回 409，弱密码与非法用户名返回 400")
    void registerValidation() {
        when(repo.userByUsername("taken")).thenReturn(Optional.of(
                account(7L, "taken", "STUDENT")));
        assertEquals(ResultCode.CONFLICT.getCode(),
                assertThrows(BizException.class, () -> service.register(
                        new AuthDTO.RegisterRequest("taken", "Password@1", "", "42"))).getCode());
        assertEquals(ResultCode.BAD_REQUEST.getCode(),
                assertThrows(BizException.class, () -> service.register(
                        new AuthDTO.RegisterRequest("ab", "Password@1", "", "42"))).getCode());
        assertEquals(ResultCode.BAD_REQUEST.getCode(),
                assertThrows(BizException.class, () -> service.register(
                        new AuthDTO.RegisterRequest("newbie", "123", "", "42"))).getCode());
    }

    @Test
    @DisplayName("退出登录递增 token_version，使旧 token 失效")
    void logoutBumpsVersion() {
        AuthContext.set(new AuthUser(7L, "student", "演示学生", "STUDENT", "42"));
        service.logout();
        verify(repo).bumpTokenVersion(7L);
    }

    @Test
    @DisplayName("未登录时 current() 返回游客视图")
    void currentGuest() {
        AuthDTO.UserView view = service.current();
        assertTrue(view.guest());
        assertEquals("GUEST", view.role());
        assertFalse(view.admin());
    }
}
