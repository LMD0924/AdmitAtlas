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

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 登录、注册、退出、角色防越权与登录失败冻结。 */
class AuthServiceTest {

    private static final String SECRET = "admitatlas-test-secret-please-change-2026";
    // 用字符数组构造，避免测试口令字面量在文件传输中被改写导致 hash / 输入不一致。
    private static final String CORRECT = new String(new char[]{
            'M', 'r', 'u', 'h', '@', '0', '0', '2', '0', '0', '9'});
    private static final String WRONG = new String(new char[]{
            'W', 'r', 'o', 'n', 'g', '@', '1', '2', '3', '4', '5'});

    private AdmitAtlasRepository repo;
    private AuthService service;
    private Map<String, Models.LoginAttempt> attempts;

    private static Models.UserAccount account(long id, String username, String role) {
        return new Models.UserAccount(id, username, PasswordHasher.hash(CORRECT),
                "演示学生", "42", role, "ACTIVE", 2, null, null, null);
    }

    @BeforeEach
    void setUp() {
        AuthContext.clear();
        repo = mock(AdmitAtlasRepository.class);
        service = new AuthService(repo, new JwtUtil(SECRET, 3600));
        // 内存状态机模拟 login_attempt 表，覆盖跨请求的失败计数与冻结。
        attempts = new HashMap<>();
        when(repo.loginAttempt(anyString())).thenAnswer(inv ->
                Optional.ofNullable(attempts.get(inv.getArgument(0, String.class))));
        doAnswer(inv -> {
            String u = inv.getArgument(0, String.class);
            int count = inv.getArgument(1, Integer.class);
            Long until = inv.getArgument(2, Long.class);
            attempts.put(u, new Models.LoginAttempt(u, count, until));
            return null;
        }).when(repo).saveLoginAttempt(anyString(), anyInt(), nullable(Long.class));
        doAnswer(inv -> {
            attempts.remove(inv.getArgument(0, String.class));
            return null;
        }).when(repo).clearLoginAttempt(anyString());
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
                new AuthDTO.LoginRequest("student", CORRECT));

        assertNotNull(res.token());
        assertEquals("Bearer", res.tokenType());
        assertEquals("STUDENT", res.user().role());
        assertFalse(res.user().admin());
        assertFalse(res.user().guest());
        verify(repo).touchLogin(7L);
    }

    @Test
    @DisplayName("密码错误返回 401，带剩余次数提示，且不泄露账号是否存在")
    void loginWrongPassword() {
        when(repo.userByUsername("student")).thenReturn(Optional.of(
                account(7L, "student", "STUDENT")));
        BizException ex = assertThrows(BizException.class, () -> service.login(
                new AuthDTO.LoginRequest("student", WRONG)));
        assertEquals(ResultCode.UNAUTHORIZED.getCode(), ex.getCode());
        assertTrue(ex.getMessage().contains("用户名或密码错误"));
        assertEquals(4, ex.getData().get("remainingAttempts"));
        assertEquals(5, ex.getData().get("maxAttempts"));
        verify(repo, never()).touchLogin(anyLong());
    }

    @Test
    @DisplayName("连续 5 次错误冻结 30 分钟，冻结期内正确密码也拒绝")
    void loginLocksAfterFiveFailures() {
        when(repo.userByUsername("student")).thenReturn(Optional.of(
                account(7L, "student", "STUDENT")));
        for (int i = 1; i <= 4; i++) {
            BizException ex = assertThrows(BizException.class, () -> service.login(
                    new AuthDTO.LoginRequest("student", WRONG)));
            assertEquals(ResultCode.UNAUTHORIZED.getCode(), ex.getCode());
            assertEquals(5 - i, ex.getData().get("remainingAttempts"));
        }
        BizException locked = assertThrows(BizException.class, () -> service.login(
                new AuthDTO.LoginRequest("student", WRONG)));
        assertEquals(ResultCode.ACCOUNT_LOCKED.getCode(), locked.getCode());
        assertEquals(1800L, locked.getData().get("retryAfterSeconds"));
        assertNotNull(locked.getData().get("lockedUntil"));

        // 冻结尚未到期，即使输入正确密码也必须拒绝（不能靠碰运气绕过）。
        BizException stillLocked = assertThrows(BizException.class, () -> service.login(
                new AuthDTO.LoginRequest("student", CORRECT)));
        assertEquals(ResultCode.ACCOUNT_LOCKED.getCode(), stillLocked.getCode());
        verify(repo, never()).touchLogin(anyLong());
    }

    @Test
    @DisplayName("冻结到期后自动清零，登录成功会清除失败计数")
    void lockExpiresAndSuccessClears() {
        when(repo.userByUsername("student")).thenReturn(Optional.of(
                account(7L, "student", "STUDENT")));
        attempts.put("student", new Models.LoginAttempt("student", 5,
                System.currentTimeMillis() - 1000L));
        AuthDTO.LoginResponse res = service.login(
                new AuthDTO.LoginRequest("student", CORRECT));
        assertNotNull(res.token());
        assertTrue(attempts.isEmpty());
        verify(repo).clearLoginAttempt("student");
    }

    @Test
    @DisplayName("停用账号返回 403")
    void loginDisabled() {
        Models.UserAccount a = account(7L, "student", "STUDENT");
        Models.UserAccount disabled = new Models.UserAccount(a.id(), a.username(),
                a.passwordHash(), a.displayName(), a.provinceCode(), a.role(),
                "DISABLED", a.tokenVersion(), null, null, null);
        when(repo.userByUsername("student")).thenReturn(Optional.of(disabled));
        BizException ex = assertThrows(BizException.class, () -> service.login(
                new AuthDTO.LoginRequest("student", CORRECT)));
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
