package com.admitatlas.common.auth;

import com.admitatlas.common.model.Models;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

/** HS256 JWT 签发 / 验签 / 篡改 / 过期。 */
class JwtUtilTest {

    private static final String SECRET = "admitatlas-test-secret-please-change-2026";

    private static Models.UserAccount account(String role) {
        return new Models.UserAccount(7L, "student", "hash", "试点学生", "42",
                role, "ACTIVE", 3, null, null, null);
    }

    @Test
    @DisplayName("签发后可还原全部 claims")
    void issueAndVerify() {
        JwtUtil jwt = new JwtUtil(SECRET, 3600);
        String token = jwt.issue(account(AuthUser.ROLE_STUDENT));
        assertEquals(3, token.split("\\.", -1).length);
        JwtUtil.Claims c = jwt.verify(token);
        assertEquals(7L, c.userId());
        assertEquals("student", c.username());
        assertEquals("STUDENT", c.role());
        assertEquals(3, c.tokenVersion());
        assertTrue(c.expiresAt() > System.currentTimeMillis() / 1000L);
        assertEquals(3600, jwt.ttlSeconds());
    }

    @Test
    @DisplayName("篡改 payload 后签名校验失败")
    void tamperedPayload() {
        JwtUtil jwt = new JwtUtil(SECRET, 3600);
        String[] parts = jwt.issue(account(AuthUser.ROLE_ADMIN)).split("\\.", -1);
        char original = parts[1].charAt(5);
        String replacement = String.valueOf(original == 'X' ? 'Y' : 'X');
        String forged = parts[0] + "." + parts[1].substring(0, 5) + replacement
                + parts[1].substring(6) + "." + parts[2];
        assertThrows(JwtUtil.JwtException.class, () -> jwt.verify(forged));
    }

    @Test
    @DisplayName("用另一把密钥验签失败")
    void wrongSecret() {
        String token = new JwtUtil(SECRET, 3600).issue(account(AuthUser.ROLE_STUDENT));
        assertThrows(JwtUtil.JwtException.class,
                () -> new JwtUtil("another-secret-that-is-long-enough", 3600).verify(token));
    }

    @Test
    @DisplayName("过期 token 被拒绝")
    void expiredToken() throws Exception {
        JwtUtil jwt = new JwtUtil(SECRET, 3600);
        ReflectionTestUtils.setField(jwt, "ttlSeconds", -60L);
        String token = jwt.issue(account(AuthUser.ROLE_STUDENT));
        JwtUtil.JwtException ex = assertThrows(JwtUtil.JwtException.class,
                () -> jwt.verify(token));
        assertTrue(ex.getMessage().contains("过期"));
    }

    @Test
    @DisplayName("空 token、格式错误、非法 Base64 均被拒绝")
    void invalidTokens() {
        JwtUtil jwt = new JwtUtil(SECRET, 3600);
        assertThrows(JwtUtil.JwtException.class, () -> jwt.verify(null));
        assertThrows(JwtUtil.JwtException.class, () -> jwt.verify("abc"));
        assertThrows(JwtUtil.JwtException.class, () -> jwt.verify("a.b.c"));
    }

    @Test
    @DisplayName("密钥过短时拒绝构造")
    void shortSecret() {
        assertThrows(IllegalArgumentException.class, () -> new JwtUtil("short", 3600));
    }
}
