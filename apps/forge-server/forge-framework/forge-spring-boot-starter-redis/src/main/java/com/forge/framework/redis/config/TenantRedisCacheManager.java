package com.forge.framework.redis.config;

import com.forge.framework.tenant.core.context.TenantContextHolder;
import org.springframework.data.redis.cache.RedisCache;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.lang.Nullable;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

public class TenantRedisCacheManager extends RedisCacheManager {

    private final Set<String> ignoreCaches;

    public TenantRedisCacheManager(RedisCacheWriter cacheWriter,
                                   RedisCacheConfiguration defaultConfig,
                                   Set<String> ignoreCaches,
                                   Map<String, RedisCacheConfiguration> initialCacheConfigurations) {
        super(cacheWriter, defaultConfig, initialCacheConfigurations);
        this.ignoreCaches = ignoreCaches == null ? Collections.emptySet() : ignoreCaches;
    }

    @Override
    @Nullable
    protected RedisCache createRedisCache(String name, RedisCacheConfiguration cacheConfig) {
        // 跨租户共享的缓存：使用原名
        if (ignoreCaches.contains(name)) {
            return super.createRedisCache(name, cacheConfig);
        }
        // 多租户缓存：加 tenantId 前缀
        Long tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null || TenantContextHolder.isIgnore()) {
            return super.createRedisCache(name, cacheConfig);
        }
        String prefixedName = tenantId + ":" + name;
        return super.createRedisCache(prefixedName, cacheConfig);
    }
}