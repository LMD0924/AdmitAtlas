package com.admitatlas.common.auth;

import com.admitatlas.common.model.Models;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 自研 HS256 JWT（纯 JDK + Jackson）：header.payload.signature，签名 HMAC-SHA256。
 *
 * <p>payload 只放 id / username / role / tokenVersion / iat / exp。tokenVersion 与
 * 数据库比对，退出登录、停用账号、重置密码后旧 token 立即失效。密钥走环境变量，
 * 仓库内只放开发默认值。
 */
public final class JwtUtil {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();
    private static final String HEADER = ENCODER.encodeToString(
            "{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));

    private final SecretKeySpec key;
    private final long ttlSeconds;

    public JwtUtil(String secret, long ttlSeconds) {
        if (secret == null || secret.length() < 16) {
            throw new IllegalArgumentException("JWT 密钥至少 16 个字符，生产环境请用环境变量注入");
        }
        this.key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        this.ttlSeconds = Math.max(60, ttlSeconds);
    }

    public record Claims(long userId, String username, String role,
                         int tokenVersion, long expiresAt) {
    }

    public String issue(Models.UserAccount account) {
        long now = System.currentTimeMillis() / 1000L;
        long exp = now + ttlSeconds;
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sub", account.username());
        payload.put("uid", account.id());
        payload.put("role", account.role());
        payload.put("ver", account.tokenVersion());
        payload.put("iat", now);
        payload.put("exp", exp);
        String body = HEADER + "." + encode(toJson(payload).getBytes(StandardCharsets.UTF_8));
        return body + "." + encode(sign(body));
    }

    public Claims verify(String token) {
        if (token == null || token.isBlank()) {
            throw new JwtException("缺少 token");
        }
        String[] parts = token.split("\\.", -1);
        if (parts.length != 3 || !HEADER.equals(parts[0])) {
            throw new JwtException("token 格式不合法");
        }
        byte[] expected = sign(parts[0] + "." + parts[1]);
        byte[] provided;
        try {
            provided = DECODER.decode(parts[2]);
        } catch (IllegalArgumentException e) {
            throw new JwtException("token 签名不合法");
        }
        if (!MessageDigest.isEqual(expected, provided)) {
            throw new JwtException("token 签名校验失败");
        }
        try {
            JsonNode node = MAPPER.readTree(DECODER.decode(parts[1]));
            long exp = node.path("exp").asLong(0);
            if (exp <= System.currentTimeMillis() / 1000L) {
                throw new JwtException("登录已过期，请重新登录");
            }
            return new Claims(node.path("uid").asLong(), node.path("sub").asText(""),
                    node.path("role").asText(""), node.path("ver").asInt(0), exp);
        } catch (JwtException e) {
            throw e;
        } catch (Exception e) {
            throw new JwtException("token 内容不合法");
        }
    }

    public long ttlSeconds() {
        return ttlSeconds;
    }

    private byte[] sign(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(key);
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("JWT 签名失败", e);
        }
    }

    private static String encode(byte[] bytes) {
        return ENCODER.encodeToString(bytes);
    }

    private static String toJson(Map<String, Object> value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("JWT payload 序列化失败", e);
        }
    }

    /** 校验失败统一抛业务语义异常，拦截器据此把请求视为游客或 401。 */
    public static class JwtException extends RuntimeException {
        public JwtException(String message) {
            super(message);
        }
    }
}
