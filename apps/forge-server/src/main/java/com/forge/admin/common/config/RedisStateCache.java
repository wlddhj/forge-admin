package com.forge.admin.common.config;

import me.zhyd.oauth.cache.AuthStateCache;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.TimeUnit;

/**
 * JustAuth Redis State 缓存实现
 * 用于在集群环境下共享 OAuth2 state 参数
 */
public class RedisStateCache implements AuthStateCache {

    private static final String KEY_PREFIX = "social_state:";

    private final StringRedisTemplate stringRedisTemplate;

    public RedisStateCache(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public void cache(String key, String value) {
        // 默认缓存5分钟
        stringRedisTemplate.opsForValue().set(KEY_PREFIX + key, value, 5, TimeUnit.MINUTES);
    }

    @Override
    public void cache(String key, String value, long timeout) {
        stringRedisTemplate.opsForValue().set(KEY_PREFIX + key, value, timeout, TimeUnit.MILLISECONDS);
    }

    @Override
    public String get(String key) {
        return stringRedisTemplate.opsForValue().get(KEY_PREFIX + key);
    }

    @Override
    public boolean containsKey(String key) {
        Boolean hasKey = stringRedisTemplate.hasKey(KEY_PREFIX + key);
        return Boolean.TRUE.equals(hasKey);
    }
}
