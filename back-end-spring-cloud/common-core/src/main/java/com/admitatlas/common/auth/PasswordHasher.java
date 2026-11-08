package com.admitatlas.common.auth;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 密码哈希：PBKDF2WithHmacSHA256 + 每账号随机盐，纯 JDK 实现，不引入第三方依赖。
 *
 * <p>存储格式 {@code pbkdf2$<iterations>$<salt-b64url>$<derived-b64url>}，
 * 与 V3__auth_roles_audit.sql 里 Python 生成的演示账号哈希完全一致。
 */
public final class PasswordHasher {

    private static final int ITERATIONS = 120_000;
    private static final int SALT_BYTES = 16;
    private static final int KEY_BITS = 256;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    private PasswordHasher() {
    }

    public static String hash(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("密码不能为空");
        }
        byte[] salt = new byte[SALT_BYTES];
        RANDOM.nextBytes(salt);
        byte[] derived = derive(raw, salt, ITERATIONS);
        return "pbkdf2$" + ITERATIONS + "$" + ENCODER.encodeToString(salt)
                + "$" + ENCODER.encodeToString(derived);
    }

    /** 校验失败（格式错误、账号不存在场景等）一律返回 false，不泄露原因。 */
    public static boolean matches(String raw, String stored) {
        if (raw == null || stored == null) {
            return false;
        }
        String[] parts = stored.split("\\$", -1);
        if (parts.length != 4 || !"pbkdf2".equals(parts[0])) {
            return false;
        }
        try {
            int iterations = Integer.parseInt(parts[1]);
            if (iterations < 10_000 || iterations > 1_000_000) {
                return false;
            }
            byte[] salt = DECODER.decode(parts[2]);
            byte[] expected = DECODER.decode(parts[3]);
            byte[] actual = derive(raw, salt, iterations);
            return MessageDigest.isEqual(expected, actual);
        } catch (Exception e) {
            return false;
        }
    }

    private static byte[] derive(String raw, byte[] salt, int iterations) {
        try {
            PBEKeySpec spec = new PBEKeySpec(raw.toCharArray(), salt, iterations, KEY_BITS);
            try {
                return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                        .generateSecret(spec).getEncoded();
            } finally {
                spec.clearPassword();
            }
        } catch (Exception e) {
            throw new IllegalStateException("密码哈希计算失败", e);
        }
    }
}
