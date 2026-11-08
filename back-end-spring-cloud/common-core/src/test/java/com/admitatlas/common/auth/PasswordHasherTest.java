package com.admitatlas.common.auth;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** 密码哈希与 V3 迁移内置账号的跨语言兼容性。 */
class PasswordHasherTest {

    /** 必须与 V3__auth_roles_audit.sql 中 demo 账号的哈希完全一致。 */
    private static final String SEEDED_DEMO_HASH =
            "pbkdf2$120000$N5EJQ5kOV1h9wwOT3UCoQw$rdwN1b6JuKa3Wu76EcLK8TTFRDwdLN7Cuzv3mSVOVxY";

    @Test
    @DisplayName("哈希后可校验，错误密码不通过")
    void hashAndMatch() {
        String stored = PasswordHasher.hash("Student@123456");
        assertTrue(stored.startsWith("pbkdf2$120000$"));
        assertTrue(PasswordHasher.matches("Student@123456", stored));
        assertFalse(PasswordHasher.matches("student@123456", stored));
        assertFalse(PasswordHasher.matches("", stored));
    }

    @Test
    @DisplayName("同一密码两次哈希盐不同、结果不同")
    void randomSalt() {
        String a = PasswordHasher.hash("Demo@123456");
        String b = PasswordHasher.hash("Demo@123456");
        assertNotEquals(a, b);
        assertTrue(PasswordHasher.matches("Demo@123456", a));
        assertTrue(PasswordHasher.matches("Demo@123456", b));
    }

    @Test
    @DisplayName("V3 SQL 里由 Python 生成的演示哈希能被 Java 校验")
    void seededHashIsCompatible() {
        assertTrue(PasswordHasher.matches("Demo@123456", SEEDED_DEMO_HASH),
                "Python hashlib.pbkdf2_hmac 与 Java PBKDF2WithHmacSHA256 必须一致");
        assertFalse(PasswordHasher.matches("Demo@12345", SEEDED_DEMO_HASH));
    }

    @Test
    @DisplayName("非法存储格式与空值不抛异常，一律返回 false")
    void malformedInput() {
        assertFalse(PasswordHasher.matches("x", null));
        assertFalse(PasswordHasher.matches(null, "pbkdf2$120000$a$b"));
        assertFalse(PasswordHasher.matches("x", "(demo-only, no login in P1)"));
        assertFalse(PasswordHasher.matches("x", "pbkdf2$not-a-number$a$b"));
        assertFalse(PasswordHasher.matches("x", "pbkdf2$1$a$b"));
    }

    @Test
    @DisplayName("空密码拒绝生成哈希")
    void blankPassword() {
        assertThrows(IllegalArgumentException.class, () -> PasswordHasher.hash("  "));
        assertThrows(IllegalArgumentException.class, () -> PasswordHasher.hash(null));
    }
}
