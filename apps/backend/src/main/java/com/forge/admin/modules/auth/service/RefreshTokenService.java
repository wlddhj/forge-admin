package com.forge.admin.modules.auth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Refresh Token 服务
 * <p>
 * 使用 Token 版本号机制实现强制下线功能：
 * - 每个用户维护一个 token_version，初始为 1
 * - Token 中包含版本号，验证时检查版本号是否匹配
 * - 强制下线时递增版本号，使所有旧 Token 失效
 * </p>
 *
 * @author standadmin
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final StringRedisTemplate stringRedisTemplate;

    private static final String REFRESH_TOKEN_PREFIX = "refresh_token:";
    private static final String TOKEN_VERSION_PREFIX = "token_version:";
    private static final Base64.Encoder BASE64_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder BASE64_DECODER = Base64.getUrlDecoder();
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /**
     * 生成 Refresh Token
     * 格式：base64(username:tokenId:salt:version):tokenValue
     *
     * @param username 用户名
     * @param expiration 过期时间（毫秒）
     * @return Refresh Token
     */
    public String generateRefreshToken(String username, long expiration) {
        String tokenId = UUID.randomUUID().toString().replace("-", "");
        String tokenValue = generateSecureToken();

        // 获取当前用户的 Token 版本号
        long version = getTokenVersion(username);

        // 生成随机盐值
        byte[] salt = new byte[16];
        SECURE_RANDOM.nextBytes(salt);
        String saltStr = BASE64_ENCODER.encodeToString(salt);

        // 存储到 Redis，key 使用 hash(username + salt + version)
        String redisKey = buildRedisKey(username, salt, version, tokenId);
        stringRedisTemplate.opsForValue().set(redisKey, tokenValue, expiration, TimeUnit.MILLISECONDS);

        // 生成客户端 token：base64(username:tokenId:salt:version):tokenValue
        String payload = username + ":" + tokenId + ":" + saltStr + ":" + version;
        String encodedPayload = BASE64_ENCODER.encodeToString(payload.getBytes(StandardCharsets.UTF_8));

        log.debug("生成 Refresh Token: username={}, tokenId={}, version={}", username, tokenId, version);
        return encodedPayload + ":" + tokenValue;
    }

    /**
     * 验证 Refresh Token 并返回用户名
     *
     * @param token Refresh Token
     * @return 用户名，如果验证失败返回 null
     */
    public String validateAndGetUsername(String token) {
        TokenInfo tokenInfo = parseToken(token);
        if (tokenInfo == null) {
            return null;
        }

        // 校验 Token 格式
        if (!validateTokenFormat(tokenInfo)) {
            log.warn("Token 格式校验失败");
            return null;
        }

        // 检查版本号是否匹配
        long currentVersion = getTokenVersion(tokenInfo.username());
        if (tokenInfo.version() != currentVersion) {
            log.debug("Token 版本号不匹配: tokenVersion={}, currentVersion={}", tokenInfo.version(), currentVersion);
            return null;
        }

        // 验证 Redis 中的 token
        String redisKey = buildRedisKey(tokenInfo.username(), tokenInfo.salt(), tokenInfo.version(), tokenInfo.tokenId());
        String storedToken = stringRedisTemplate.opsForValue().get(redisKey);

        if (tokenInfo.tokenValue().equals(storedToken)) {
            return tokenInfo.username();
        }

        return null;
    }

    /**
     * 验证 Refresh Token
     *
     * @param token Refresh Token
     * @return 是否有效
     */
    public boolean validateRefreshToken(String token) {
        return validateAndGetUsername(token) != null;
    }

    /**
     * 删除 Refresh Token（登出时调用）
     *
     * @param token Refresh Token
     */
    public void deleteRefreshToken(String token) {
        TokenInfo tokenInfo = parseToken(token);
        if (tokenInfo == null) {
            return;
        }

        String redisKey = buildRedisKey(tokenInfo.username(), tokenInfo.salt(), tokenInfo.version(), tokenInfo.tokenId());
        stringRedisTemplate.delete(redisKey);

        log.debug("删除 Refresh Token: username={}, tokenId={}", tokenInfo.username(), tokenInfo.tokenId());
    }

    /**
     * 删除用户所有 Refresh Token（用于强制下线等场景）
     * 通过递增版本号使所有旧 Token 失效，无需遍历 Redis
     *
     * @param username 用户名
     */
    public void deleteAllRefreshTokens(String username) {
        // 递增版本号，使所有旧 Token 失效
        String versionKey = TOKEN_VERSION_PREFIX + username;
        Long newVersion = stringRedisTemplate.opsForValue().increment(versionKey);

        // 设置版本号永不过期
        if (newVersion != null && newVersion == 1) {
            stringRedisTemplate.persist(versionKey);
        }

        log.info("用户 Token 版本号已更新: username={}, newVersion={}", username, newVersion);
    }

    /**
     * 获取用户的 Token 版本号
     *
     * @param username 用户名
     * @return 版本号，默认为 1
     */
    private long getTokenVersion(String username) {
        String versionKey = TOKEN_VERSION_PREFIX + username;
        String versionStr = stringRedisTemplate.opsForValue().get(versionKey);
        if (versionStr == null) {
            // 初始化版本号为 1
            stringRedisTemplate.opsForValue().set(versionKey, "1");
            return 1;
        }
        return Long.parseLong(versionStr);
    }

    /**
     * 校验 Token 格式
     */
    private boolean validateTokenFormat(TokenInfo tokenInfo) {
        // username 长度限制
        if (tokenInfo.username() == null || tokenInfo.username().length() > 50 || tokenInfo.username().isEmpty()) {
            return false;
        }
        // tokenId 应该是 32 位 UUID（不带横线）
        if (tokenInfo.tokenId() == null || tokenInfo.tokenId().length() != 32) {
            return false;
        }
        // tokenValue 长度限制（32 字节 base64 编码后约 44 字符）
        if (tokenInfo.tokenValue() == null || tokenInfo.tokenValue().length() > 100) {
            return false;
        }
        // version 必须大于 0
        if (tokenInfo.version() <= 0) {
            return false;
        }
        return true;
    }

    /**
     * 解析 Token
     */
    private TokenInfo parseToken(String token) {
        if (token == null) {
            return null;
        }

        String[] parts = token.split(":");
        if (parts.length != 2) {
            return null;
        }

        try {
            String encodedPayload = parts[0];
            String tokenValue = parts[1];
            String payload = new String(BASE64_DECODER.decode(encodedPayload), StandardCharsets.UTF_8);
            String[] payloadParts = payload.split(":");

            if (payloadParts.length != 4) {
                return null;
            }

            String username = payloadParts[0];
            String tokenId = payloadParts[1];
            byte[] salt = BASE64_DECODER.decode(payloadParts[2]);
            long version = Long.parseLong(payloadParts[3]);

            return new TokenInfo(username, tokenId, salt, version, tokenValue);
        } catch (Exception e) {
            log.debug("解析 Refresh Token 失败");
            return null;
        }
    }

    /**
     * 构建 Redis Key
     */
    private String buildRedisKey(String username, byte[] salt, long version, String tokenId) {
        String hashInput = username + ":" + BASE64_ENCODER.encodeToString(salt) + ":" + version;
        String hashedKey = sha256Hash(hashInput);
        return REFRESH_TOKEN_PREFIX + hashedKey + ":" + tokenId;
    }

    /**
     * 生成安全的随机 Token
     */
    private String generateSecureToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return BASE64_ENCODER.encodeToString(bytes);
    }

    /**
     * SHA-256 哈希
     */
    private String sha256Hash(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return BASE64_ENCODER.encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    /**
     * Token 信息记录
     */
    private record TokenInfo(String username, String tokenId, byte[] salt, long version, String tokenValue) {}
}
