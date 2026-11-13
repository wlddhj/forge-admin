package com.forge.modules.plugin.state;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * 插件缓存服务：启停状态 / 授权 scope / 启用编码集（Redis，TTL 300s，
 * 变更方主动 evict；失败由 TTL 兜底）。菜单缓存（menu::user:*）走
 * CacheManager 全清，与菜单管理页删除行为一致。
 *
 * @author forge-admin
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PluginCacheService {

    static final Duration TTL = Duration.ofSeconds(300);

    static final String KEY_STATE = "plugin:state:%d:%s";

    static final String KEY_SCOPE = "plugin:scope:%d:%s";

    static final String KEY_ENABLED_CODES = "plugin:codes:%d";

    private final StringRedisTemplate redisTemplate;

    private final CacheManager cacheManager;

    // ===== 启停状态 =====

    String getState(Long tenantId, String pluginCode) {
        return redisTemplate.opsForValue().get(key(KEY_STATE, tenantId, pluginCode));
    }

    void putState(Long tenantId, String pluginCode, boolean enabled) {
        redisTemplate.opsForValue().set(key(KEY_STATE, tenantId, pluginCode), enabled ? "1" : "0", TTL);
    }

    public void evictState(Long tenantId, String pluginCode) {
        redisTemplate.delete(key(KEY_STATE, tenantId, pluginCode));
    }

    // ===== 授权 scope =====

    public String getScope(Long tenantId, String pluginCode) {
        return redisTemplate.opsForValue().get(key(KEY_SCOPE, tenantId, pluginCode));
    }

    public void putScope(Long tenantId, String pluginCode, String json) {
        redisTemplate.opsForValue().set(key(KEY_SCOPE, tenantId, pluginCode), json, TTL);
    }

    public void evictScope(Long tenantId, String pluginCode) {
        redisTemplate.delete(key(KEY_SCOPE, tenantId, pluginCode));
    }

    // ===== 租户启用插件编码集 =====

    String getEnabledCodes(Long tenantId) {
        return redisTemplate.opsForValue().get(String.format(KEY_ENABLED_CODES, tenantId));
    }

    void putEnabledCodes(Long tenantId, String json) {
        redisTemplate.opsForValue().set(String.format(KEY_ENABLED_CODES, tenantId), json, TTL);
    }

    void evictEnabledCodes(Long tenantId) {
        redisTemplate.delete(String.format(KEY_ENABLED_CODES, tenantId));
    }

    /** 全量失效该租户插件相关缓存（启停/授权变更时） */
    public void evictTenantCaches(Long tenantId, String pluginCode) {
        try {
            evictState(tenantId, pluginCode);
            evictScope(tenantId, pluginCode);
            evictEnabledCodes(tenantId);
            evictMenuCache();
        } catch (Exception e) {
            // Redis 不可达：落库成功即可，evict 失败由 TTL 300s/3600s 兜底
            log.warn("插件缓存 evict 失败（由 TTL 兜底）: tenant={} plugin={}", tenantId, pluginCode, e);
        }
    }

    /** 菜单缓存全清（menu::user:*，key 无租户段，全清最简且与菜单管理页行为一致） */
    public void evictMenuCache() {
        Cache menuCache = cacheManager.getCache("menu");
        if (menuCache != null) {
            menuCache.clear();
        }
    }

    private String key(String pattern, Long tenantId, String pluginCode) {
        return String.format(pattern, tenantId, pluginCode);
    }
}
