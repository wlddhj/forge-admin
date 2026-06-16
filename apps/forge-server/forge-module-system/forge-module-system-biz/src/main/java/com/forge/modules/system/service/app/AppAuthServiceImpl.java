package com.forge.modules.system.service.app;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.forge.framework.security.config.JwtProperties;
import com.forge.modules.system.dto.app.AppLoginResponse;
import com.forge.modules.system.dto.app.AppUserProfileResponse;
import com.forge.modules.system.entity.AppUser;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class AppAuthServiceImpl implements AppAuthService {

    private final AppUserService appUserService;
    private final JwtProperties jwtProperties;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${forge.wx.mini-app-id:}")
    private String appId;

    @Value("${forge.wx.mini-app-secret:}")
    private String appSecret;

    private static final String REFRESH_TOKEN_PREFIX = "app_refresh_token:";
    private static final String SESSION_PREFIX = "app_session:";
    private static final String USER_SESSIONS_PREFIX = "app_user_sessions:";
    private static final String WX_CODE2SESSION_URL = "https://api.weixin.qq.com/sns/jscode2session?appid=%s&secret=%s&js_code=%s&grant_type=authorization_code";

    @Override
    public AppLoginResponse wxLogin(String code) {
        String openId = getWxOpenId(code);
        if (openId == null) {
            throw new RuntimeException("微信登录失败，无法获取用户标识");
        }

        AppUser user = appUserService.getByOpenId(openId);
        if (user == null) {
            user = AppUser.builder()
                    .openId(openId)
                    .status(1)
                    .lastLoginTime(LocalDateTime.now())
                    .build();
            appUserService.createAppUser(user);
        } else {
            appUserService.updateLastLoginTime(user.getId());
        }

        String tokenId = UUID.randomUUID().toString().replace("-", "");
        String accessToken = generateAppToken(user.getId().toString(), tokenId);
        String refreshToken = generateRefreshToken(user.getId().toString());

        saveAppSession(tokenId, user.getId(), user.getOpenId(), refreshToken);

        AppUserProfileResponse profile = appUserService.getProfile(user.getId());
        return AppLoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtProperties.getExpiration())
                .refreshExpiresIn(jwtProperties.getRefreshExpiration())
                .userInfo(profile)
                .build();
    }

    @Override
    public AppLoginResponse refreshToken(String refreshToken) {
        String redisKey = REFRESH_TOKEN_PREFIX + refreshToken;
        String userIdStr = stringRedisTemplate.opsForValue().get(redisKey);
        if (userIdStr == null) {
            throw new RuntimeException("刷新令牌无效或已过期");
        }

        stringRedisTemplate.delete(redisKey);

        Long userId = Long.parseLong(userIdStr);
        AppUser user = appUserService.getById(userId);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }

        String tokenId = UUID.randomUUID().toString().replace("-", "");
        String accessToken = generateAppToken(userId.toString(), tokenId);
        String newRefreshToken = generateRefreshToken(userId.toString());

        saveAppSession(tokenId, userId, user.getOpenId(), newRefreshToken);

        AppUserProfileResponse profile = appUserService.getProfile(userId);
        return AppLoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(newRefreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtProperties.getExpiration())
                .refreshExpiresIn(jwtProperties.getRefreshExpiration())
                .userInfo(profile)
                .build();
    }

    @Override
    public void logout(String accessToken, String refreshToken) {
        String userIdStr = null;
        String tokenId = null;

        // 从 accessToken 中解析 tokenId 和 userId
        if (accessToken != null) {
            try {
                tokenId = parseTokenIdFromAccessToken(accessToken);
                userIdStr = parseUserIdFromAccessToken(accessToken);
            } catch (Exception e) {
                log.warn("解析 accessToken 失败", e);
            }
        }

        // 从 session SET 中移除
        if (userIdStr != null) {
            String sessionSetKey = USER_SESSIONS_PREFIX + userIdStr;
            if (tokenId != null) {
                stringRedisTemplate.opsForSet().remove(sessionSetKey, "tok_" + tokenId);
            }
            if (refreshToken != null) {
                stringRedisTemplate.opsForSet().remove(sessionSetKey, refreshToken);
            }
        }

        // 删除 refresh_token
        if (refreshToken != null) {
            stringRedisTemplate.delete(REFRESH_TOKEN_PREFIX + refreshToken);
        }

        // 删除 session
        if (tokenId != null) {
            stringRedisTemplate.delete(SESSION_PREFIX + tokenId);
        }
    }

    private String getWxOpenId(String code) {
        // Mock 模式：如果 appId 未配置（空白字符串），返回模拟 openId 用于开发测试
        log.debug("wxLogin appId value: '{}', length: {}, isEmpty: {}, isBlank: {}",
            appId, appId == null ? "null" : appId.length(), appId == null, appId != null && appId.isBlank());
        if (appId == null || appId.isBlank()) {
            log.info("微信小程序 appId 未配置，使用 Mock 模式，code: {}", code);
            return "mock_openid_" + code.hashCode();
        }
        try {
            String url = String.format(WX_CODE2SESSION_URL, appId, appSecret, code);
            RestTemplate restTemplate = new RestTemplate();
            String response = restTemplate.getForObject(url, String.class);
            JsonNode json = objectMapper.readTree(response);
            if (json.has("openid")) {
                return json.get("openid").asText();
            }
            log.error("微信登录失败: {}", response);
            return null;
        } catch (Exception e) {
            log.error("调用微信接口异常", e);
            return null;
        }
    }

    private String generateAppToken(String subject, String tokenId) {
        SecretKey key = Keys.hmacShaKeyFor(jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8));
        Date now = new Date();
        Date expiry = new Date(now.getTime() + jwtProperties.getExpiration());
        return Jwts.builder()
                .subject(subject)
                .claim("tokenId", tokenId)
                .claim("type", "app")
                .issuedAt(now)
                .expiration(expiry)
                .signWith(key)
                .compact();
    }

    private String generateRefreshToken(String userId) {
        String token = UUID.randomUUID().toString().replace("-", "");
        stringRedisTemplate.opsForValue().set(
                REFRESH_TOKEN_PREFIX + token,
                userId,
                jwtProperties.getRefreshExpiration(),
                TimeUnit.MILLISECONDS);
        return token;
    }

    private void saveAppSession(String tokenId, Long userId, String openId, String refreshToken) {
        // 保存 session 信息
        stringRedisTemplate.opsForValue().set(
                SESSION_PREFIX + tokenId,
                userId + ":" + openId,
                jwtProperties.getRefreshExpiration(),
                TimeUnit.MILLISECONDS);

        // 维护用户 sessions SET，用于强制下线
        String sessionSetKey = USER_SESSIONS_PREFIX + userId;
        stringRedisTemplate.opsForSet().add(sessionSetKey, "tok_" + tokenId);
        stringRedisTemplate.opsForSet().add(sessionSetKey, refreshToken);
        stringRedisTemplate.expire(sessionSetKey, jwtProperties.getRefreshExpiration(), TimeUnit.MILLISECONDS);
    }

    private String parseTokenIdFromAccessToken(String accessToken) {
        SecretKey key = Keys.hmacShaKeyFor(jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8));
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(accessToken)
                .getPayload();
        return claims.get("tokenId", String.class);
    }

    private String parseUserIdFromAccessToken(String accessToken) {
        SecretKey key = Keys.hmacShaKeyFor(jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8));
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(accessToken)
                .getPayload();
        return claims.getSubject();
    }
}
