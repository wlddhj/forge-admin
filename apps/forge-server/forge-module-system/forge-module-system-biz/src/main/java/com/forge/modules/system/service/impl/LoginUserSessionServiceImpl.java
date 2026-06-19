package com.forge.modules.system.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.forge.modules.system.auth.service.RefreshTokenService;
import com.forge.modules.system.dto.online.LoginUserSession;
import com.forge.modules.system.service.LoginUserSessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 登录用户会话服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LoginUserSessionServiceImpl implements LoginUserSessionService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    @Lazy
    @Autowired
    private RefreshTokenService refreshTokenService;

    private static final String LOGIN_TOKEN_KEY = "login_tokens:";

    /**
     * 闲置超时时间：10分钟（毫秒）
     */
    private static final long IDLE_TIMEOUT_MS = 10 * 60 * 1000L;

    /**
     * 离线超时时间：30分钟（毫秒）
     */
    private static final long OFFLINE_TIMEOUT_MS = 30 * 60 * 1000L;

    @Override
    public void saveSession(LoginUserSession session, long ttl) {
        String key = LOGIN_TOKEN_KEY + session.getTokenId();
        // 设置初始最后活跃时间为登录时间
        if (session.getLastActiveTime() == null) {
            session.setLastActiveTime(session.getLoginTime());
        }
        redisTemplate.opsForValue().set(key, session, ttl, TimeUnit.MILLISECONDS);
        log.debug("保存登录会话: tokenId={}, username={}", session.getTokenId(), session.getUsername());
    }

    @Override
    public List<LoginUserSession> getAllSessions() {
        List<LoginUserSession> sessions = new ArrayList<>();
        long now = System.currentTimeMillis();

        Set<String> keys = redisTemplate.keys(LOGIN_TOKEN_KEY + "*");
        if (keys == null || keys.isEmpty()) {
            return sessions;
        }

        for (String key : keys) {
            Object value = redisTemplate.opsForValue().get(key);
            LoginUserSession session = null;

            if (value instanceof LoginUserSession) {
                session = (LoginUserSession) value;
            } else if (value instanceof Map) {
                // Jackson 反序列化时可能返回 LinkedHashMap，需要手动转换
                try {
                    session = objectMapper.convertValue(value, LoginUserSession.class);
                } catch (Exception e) {
                    log.warn("转换登录会话失败: key={}, error={}", key, e.getMessage());
                }
            }

            if (session != null) {
                // 过滤超过30分钟无心跳的会话（视为离线）
                // 使用 loginTime 作为备用判断（兼容旧数据没有 lastActiveTime 的情况）
                Long lastActiveTime = session.getLastActiveTime();
                Long checkTime = lastActiveTime != null ? lastActiveTime : session.getLoginTime();
                if (checkTime != null && (now - checkTime) > OFFLINE_TIMEOUT_MS) {
                    // 删除过期的会话
                    redisTemplate.delete(key);
                    log.debug("清理离线会话: tokenId={}, username={}", session.getTokenId(), session.getUsername());
                    continue;
                }
                sessions.add(session);
            }
        }

        // 按登录时间倒序排列
        sessions.sort((a, b) -> Long.compare(b.getLoginTime(), a.getLoginTime()));

        return sessions;
    }

    @Override
    public Long getSessionTTL(String tokenId) {
        String key = LOGIN_TOKEN_KEY + tokenId;
        return redisTemplate.getExpire(key, TimeUnit.SECONDS);
    }

    @Override
    public void deleteSession(String tokenId) {
        String key = LOGIN_TOKEN_KEY + tokenId;
        // 删除前先取出 session，用于同步清理 refreshToken
        Object value = redisTemplate.opsForValue().get(key);
        LoginUserSession session = convertToSession(value);
        if (session != null && session.getRefreshToken() != null) {
            try {
                refreshTokenService.deleteRefreshToken(session.getRefreshToken());
            } catch (Exception e) {
                log.warn("清理 RefreshToken 失败: tokenId={}, error={}", tokenId, e.getMessage());
            }
        }
        redisTemplate.delete(key);
        log.info("删除登录会话: tokenId={}", tokenId);
    }

    @Override
    public void updateLastActiveTime(String tokenId) {
        String key = LOGIN_TOKEN_KEY + tokenId;
        Object value = redisTemplate.opsForValue().get(key);
        if (value == null) {
            log.warn("更新心跳失败，会话不存在: tokenId={}", tokenId);
            return;
        }

        LoginUserSession session;
        if (value instanceof LoginUserSession) {
            session = (LoginUserSession) value;
        } else if (value instanceof Map) {
            session = objectMapper.convertValue(value, LoginUserSession.class);
        } else {
            log.warn("更新心跳失败，会话类型错误: tokenId={}", tokenId);
            return;
        }

        // 更新最后活跃时间，保留原有 TTL
        Long ttl = redisTemplate.getExpire(key, TimeUnit.MILLISECONDS);
        if (ttl != null && ttl > 0) {
            session.setLastActiveTime(System.currentTimeMillis());
            redisTemplate.opsForValue().set(key, session, ttl, TimeUnit.MILLISECONDS);
            log.debug("更新心跳: tokenId={}, username={}", tokenId, session.getUsername());
        }
    }

    @Override
    public List<LoginUserSession> getSessionsByUsername(String username) {
        List<LoginUserSession> result = new ArrayList<>();
        if (username == null || username.isEmpty()) {
            return result;
        }

        Set<String> keys = redisTemplate.keys(LOGIN_TOKEN_KEY + "*");
        if (keys == null || keys.isEmpty()) {
            return result;
        }

        for (String key : keys) {
            Object value = redisTemplate.opsForValue().get(key);
            LoginUserSession session = convertToSession(value);
            if (session != null && username.equals(session.getUsername())) {
                result.add(session);
            }
        }

        result.sort((a, b) -> Long.compare(b.getLoginTime(), a.getLoginTime()));
        return result;
    }

    @Override
    public int kickOutUserSessions(String username, String excludeTokenId) {
        List<LoginUserSession> sessions = getSessionsByUsername(username);
        int kicked = 0;
        for (LoginUserSession session : sessions) {
            if (excludeTokenId != null && excludeTokenId.equals(session.getTokenId())) {
                continue;
            }
            // 同步清理关联的 refreshToken，防止前端用 refreshToken 换新 token 绕过限制
            if (session.getRefreshToken() != null) {
                try {
                    refreshTokenService.deleteRefreshToken(session.getRefreshToken());
                } catch (Exception e) {
                    log.warn("清理 RefreshToken 失败: username={}, tokenId={}, error={}",
                            username, session.getTokenId(), e.getMessage());
                }
            }
            String key = LOGIN_TOKEN_KEY + session.getTokenId();
            redisTemplate.delete(key);
            kicked++;
            log.info("单点登录踢出旧会话: username={}, tokenId={}", username, session.getTokenId());
        }
        return kicked;
    }

    /**
     * 将 Redis 中的值转换为 LoginUserSession
     */
    private LoginUserSession convertToSession(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LoginUserSession session) {
            return session;
        }
        if (value instanceof Map map) {
            try {
                return objectMapper.convertValue(map, LoginUserSession.class);
            } catch (Exception e) {
                log.warn("转换登录会话失败: error={}", e.getMessage());
            }
        }
        return null;
    }
}
