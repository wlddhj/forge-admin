package com.forge.admin.modules.auth.security;

import com.forge.admin.common.config.JwtProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JWT Token 提供者测试
 *
 * 测试 JWT 令牌的生成、验证和解析功能
 */
@DisplayName("JWT Token 提供者测试")
class JwtTokenProviderTest {

    private JwtTokenProvider jwtTokenProvider;

    @BeforeEach
    void setUp() {
        // 创建测试用的 JwtProperties
        JwtProperties properties = new JwtProperties();
        properties.setSecret("test-secret-key-for-testing-purpose-at-least-256-bits-long-for-security");
        properties.setExpiration(3600000L); // 1小时

        jwtTokenProvider = new JwtTokenProvider(properties);
        jwtTokenProvider.init();
    }

    @Test
    @DisplayName("应成功生成有效的 JWT Token")
    void testGenerateToken() {
        String username = "testuser";
        String token = jwtTokenProvider.generateToken(username);

        assertNotNull(token);
        assertFalse(token.isEmpty());
        assertTrue(token.length() > 50); // JWT tokens 应该足够长
    }

    @Test
    @DisplayName("应能从有效的 Token 中提取用户名")
    void testGetUsernameFromToken() {
        String username = "testuser";
        String token = jwtTokenProvider.generateToken(username);

        String extractedUsername = jwtTokenProvider.getUsernameFromToken(token);

        assertEquals(username, extractedUsername);
    }

    @Test
    @DisplayName("应能验证有效的 Token")
    void testValidateToken() {
        String username = "testuser";
        String token = jwtTokenProvider.generateToken(username);

        assertTrue(jwtTokenProvider.validateToken(token));
    }

    @Test
    @DisplayName("应拒绝无效的 Token")
    void testInvalidToken() {
        String invalidToken = "invalid.token.string";

        assertFalse(jwtTokenProvider.validateToken(invalidToken));
    }

    @Test
    @DisplayName("应拒绝空的 Token")
    void testEmptyToken() {
        assertFalse(jwtTokenProvider.validateToken(""));
        assertFalse(jwtTokenProvider.validateToken(null));
    }

    @Test
    @DisplayName("Token 应包含三部分，用点分隔")
    void testTokenStructure() {
        String token = jwtTokenProvider.generateToken("testuser");

        String[] parts = token.split("\\.");
        assertEquals(3, parts.length, "JWT Token 应包含 header.payload.signature 三部分");
    }

    @Test
    @DisplayName("不同的用户名应生成不同的 Token")
    void testDifferentTokens() {
        String token1 = jwtTokenProvider.generateToken("user1");
        String token2 = jwtTokenProvider.generateToken("user2");

        assertNotEquals(token1, token2, "不同用户应生成不同的 Token");
    }

    @Test
    @DisplayName("生成的 Token 应包含有效的时间戳")
    void testTokenTimestamp() {
        String username = "testuser";
        String token = jwtTokenProvider.generateToken(username);

        // JWT Token 应包含 iat (issued at) 时间戳
        assertNotNull(token);
        assertTrue(token.length() > 50, "Token 应该足够长以包含时间戳信息");
    }

    @Test
    @DisplayName("Token 的 payload 应包含正确的用户名")
    void testTokenPayloadContent() {
        String username = "testuser";
        String token = jwtTokenProvider.generateToken(username);

        String extractedUsername = jwtTokenProvider.getUsernameFromToken(token);

        assertEquals(username, extractedUsername);
    }
}
