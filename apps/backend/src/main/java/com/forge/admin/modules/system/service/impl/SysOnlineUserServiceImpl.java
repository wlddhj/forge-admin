package com.forge.admin.modules.system.service.impl;

import com.forge.admin.modules.system.dto.online.OnlineUserResponse;
import com.forge.admin.modules.system.service.SysOnlineUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 在线用户服务实现
 */
@Service
@RequiredArgsConstructor
public class SysOnlineUserServiceImpl implements SysOnlineUserService {

    private final RedisTemplate<String, Object> redisTemplate;

    private static final String LOGIN_TOKEN_KEY = "login_tokens:";

    @Override
    public List<OnlineUserResponse> getOnlineUsers() {
        List<OnlineUserResponse> onlineUsers = new ArrayList<>();

        // 获取所有登录token
        Set<String> keys = redisTemplate.keys(LOGIN_TOKEN_KEY + "*");
        if (keys == null || keys.isEmpty()) {
            return onlineUsers;
        }

        for (String key : keys) {
            Object value = redisTemplate.opsForValue().get(key);
            if (value instanceof java.util.Map) {
                @SuppressWarnings("unchecked")
                java.util.Map<String, Object> userInfo = (java.util.Map<String, Object>) value;

                OnlineUserResponse user = new OnlineUserResponse();
                user.setTokenId(key.replace(LOGIN_TOKEN_KEY, ""));
                user.setUserId(Long.valueOf(userInfo.get("userId").toString()));
                user.setUsername((String) userInfo.get("username"));
                user.setNickname((String) userInfo.get("nickname"));
                user.setLoginIp((String) userInfo.get("loginIp"));
                user.setLoginLocation((String) userInfo.get("loginLocation"));
                user.setBrowser((String) userInfo.get("browser"));
                user.setOs((String) userInfo.get("os"));
                user.setLoginTime(Long.valueOf(userInfo.get("loginTime").toString()));

                // 获取剩余过期时间
                Long ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);
                user.setTtl(ttl);

                onlineUsers.add(user);
            }
        }

        // 按登录时间倒序排列
        onlineUsers.sort((a, b) -> Long.compare(b.getLoginTime(), a.getLoginTime()));

        return onlineUsers;
    }

    @Override
    public void forceLogout(String tokenId) {
        String key = LOGIN_TOKEN_KEY + tokenId;
        redisTemplate.delete(key);
    }
}
