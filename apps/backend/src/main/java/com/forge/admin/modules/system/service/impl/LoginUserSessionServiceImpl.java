package com.forge.admin.modules.system.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.forge.admin.modules.system.dto.online.LoginUserSession;
import com.forge.admin.modules.system.service.LoginUserSessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    private static final String LOGIN_TOKEN_KEY = "login_tokens:";

    @Override
    public void saveSession(LoginUserSession session, long ttl) {
        String key = LOGIN_TOKEN_KEY + session.getTokenId();
        redisTemplate.opsForValue().set(key, session, ttl, TimeUnit.MILLISECONDS);
        log.debug("保存登录会话: tokenId={}, username={}", session.getTokenId(), session.getUsername());
    }

    @Override
    public List<LoginUserSession> getAllSessions() {
        List<LoginUserSession> sessions = new ArrayList<>();

        Set<String> keys = redisTemplate.keys(LOGIN_TOKEN_KEY + "*");
        if (keys == null || keys.isEmpty()) {
            return sessions;
        }

        for (String key : keys) {
            Object value = redisTemplate.opsForValue().get(key);
            if (value instanceof LoginUserSession) {
                sessions.add((LoginUserSession) value);
            } else if (value instanceof Map) {
                // Jackson 反序列化时可能返回 LinkedHashMap，需要手动转换
                try {
                    LoginUserSession session = objectMapper.convertValue(value, LoginUserSession.class);
                    sessions.add(session);
                } catch (Exception e) {
                    log.warn("转换登录会话失败: key={}, error={}", key, e.getMessage());
                }
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
        redisTemplate.delete(key);
        log.info("删除登录会话: tokenId={}", tokenId);
    }
}
