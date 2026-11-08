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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** F-C-02：个人中心的数据隔离、资料校验与改密码吊销会话。 */
class ProfileServiceTest {

    private static final long USER_ID = 7L;
    private static final String OLD_PASSWORD = "OldPass@123";

    private AdmitAtlasRepository repo;
    private ProfileService service;
    private Models.UserAccount account;

    private static Models.UserAccount account(String displayName, String province, String hash) {
        return new Models.UserAccount(USER_ID, "student", hash, displayName, province,
                "STUDENT", "ACTIVE", 2, "2026-11-08 10:00:00", "2026-11-01 09:00:00");
    }

    @BeforeEach
    void setUp() {
        AuthContext.clear();
        AuthContext.set(new AuthUser(USER_ID, "student", "试点学生", "STUDENT", "42"));
        account = account("试点学生", "42", PasswordHasher.hash(OLD_PASSWORD));
        repo = mock(AdmitAtlasRepository.class);
        when(repo.userById(USER_ID)).thenReturn(Optional.of(account));
        when(repo.province("42")).thenReturn(Optional.of(
                new Models.Province("42", "湖北", "GROUP_3_1_2", "2026-v1")));
        when(repo.province("11")).thenReturn(Optional.of(
                new Models.Province("11", "北京", "GROUP_3_3", "2026-v1")));
        when(repo.latestDataVersion()).thenReturn("demo-2026-hb-v1");
        service = new ProfileService(repo);
    }

    @AfterEach
    void tearDown() {
        AuthContext.clear();
    }

    private void stubDetail() {
        Map<String, Integer> stats = new LinkedHashMap<>();
        stats.put("scoreCount", 1);
        stats.put("taskCount", 2);
        stats.put("planCount", 3);
        stats.put("itemCount", 9);
        when(repo.userStats(USER_ID)).thenReturn(stats);
        when(repo.latestScore(USER_ID, 2026)).thenReturn(Optional.empty());
        when(repo.plans(USER_ID)).thenReturn(List.of());
        when(repo.tasksByUser(USER_ID, 5)).thenReturn(List.of());
    }

    @Test
    @DisplayName("详情只包含当前账号数据，且响应不含密码哈希")
    void detailIsIsolatedAndNeverLeaksHash() {
        stubDetail();
        Map<String, Object> data = service.detail();

        verify(repo).userStats(USER_ID);
        verify(repo, never()).userStats(8L);
        verify(repo).plans(USER_ID);
        verify(repo).tasksByUser(USER_ID, 5);

        @SuppressWarnings("unchecked")
        Map<String, Object> profile = (Map<String, Object>) data.get("profile");
        assertEquals("student", profile.get("username"));
        assertEquals("湖北", profile.get("provinceName"));
        assertEquals("学生", profile.get("roleLabel"));
        assertEquals("正常", profile.get("statusLabel"));
        assertFalse(data.toString().contains("pbkdf2$"), "响应不得包含密码哈希");
        assertFalse(profile.containsKey("passwordHash"));
        assertEquals(3, ((Map<?, ?>) data.get("stats")).get("planCount"));
    }

    @Test
    @DisplayName("资料更新写入昵称与省份，返回新 UserView 且不改角色")
    void updateProfile() {
        when(repo.userById(USER_ID)).thenReturn(Optional.of(account),
                Optional.of(account("新昵称", "11", account.passwordHash())));

        AuthDTO.UserView view = service.update(
                new ProfileDTO.ProfileRequest("  新昵称  ", "11"));

        verify(repo).updateUserProfile(USER_ID, "新昵称", "11");
        verify(repo).writeAudit(eq(USER_ID), eq("student"), eq("PROFILE_UPDATE"),
                eq("USER"), eq("7"), contains("→"));
        assertEquals("新昵称", view.displayName());
        assertEquals("11", view.provinceCode());
        assertEquals("STUDENT", view.role(), "自助修改资料不能改变角色");
        assertEquals("student", view.username(), "用户名不可自助修改");
    }

    @Test
    @DisplayName("空昵称 / 非法省份 / 超长昵称一律拒绝")
    void updateValidation() {
        for (ProfileDTO.ProfileRequest bad : List.of(
                new ProfileDTO.ProfileRequest("  ", "42"),
                new ProfileDTO.ProfileRequest("新昵称", "99"),
                new ProfileDTO.ProfileRequest("x".repeat(33), "42"),
                new ProfileDTO.ProfileRequest(null, "42"))) {
            BizException ex = assertThrows(BizException.class, () -> service.update(bad));
            assertEquals(ResultCode.BAD_REQUEST.getCode(), ex.getCode());
        }
        verify(repo, never()).updateUserProfile(anyLong(), anyString(), anyString());
    }

    @Test
    @DisplayName("原密码错误、新密码过短或与旧密码相同都拒绝修改")
    void changePasswordValidation() {
        BizException wrong = assertThrows(BizException.class, () -> service.changePassword(
                new ProfileDTO.PasswordRequest("WrongPass@1", "NewPass@123")));
        assertEquals(ResultCode.BAD_REQUEST.getCode(), wrong.getCode());
        assertTrue(wrong.getMessage().contains("原密码"));

        assertThrows(BizException.class, () -> service.changePassword(
                new ProfileDTO.PasswordRequest(OLD_PASSWORD, "short")));
        assertThrows(BizException.class, () -> service.changePassword(
                new ProfileDTO.PasswordRequest(OLD_PASSWORD, OLD_PASSWORD)));
        verify(repo, never()).updatePassword(anyLong(), anyString());
    }

    @Test
    @DisplayName("改密码成功后更新哈希、写审计并声明会话已吊销")
    void changePasswordRevokesSession() {
        Map<String, Object> data = service.changePassword(
                new ProfileDTO.PasswordRequest(OLD_PASSWORD, "NewPass@123"));

        verify(repo).updatePassword(eq(USER_ID), argThat(h ->
                h.startsWith("pbkdf2$") && PasswordHasher.matches("NewPass@123", h)));
        verify(repo).writeAudit(eq(USER_ID), eq("student"), eq("PASSWORD_CHANGE"),
                eq("USER"), eq("7"), anyString());
        assertEquals(true, data.get("changed"));
        assertEquals(true, data.get("sessionRevoked"));
    }
}
