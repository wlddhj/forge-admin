package com.forge.modules.workflow.im;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ConcurrentHashMap;

/**
 * IM 渠道基类：access_token 按租户分桶缓存 + 失效强刷。
 * <p>
 * 缓存有效期取平台 expires_in 减安全余量；{@link ConcurrentHashMap#compute} 保证
 * 同租户并发刷新只执行一次网络调用（不同租户互不影响）。每实例各自持 token
 * （平台侧 token 互不失效，仅消耗获取配额），v1 不引入 Redis 共享。
 *
 * @author forge-admin
 */
@Slf4j
public abstract class AbstractTokenChannel implements ImNotificationChannel {

    /**
     * token 安全余量（秒）：提前过期，避免边界请求
     */
    private static final long TOKEN_SAFETY_MARGIN_SECONDS = 120;

    private final ConcurrentHashMap<Long, CachedToken> tokenCache = new ConcurrentHashMap<>();

    /**
     * 获取 access_token（缓存命中直接返回；forceRefresh 强制重新获取）
     */
    protected String getToken(Long tenantId, boolean forceRefresh) {
        CachedToken current = tokenCache.get(tenantId);
        if (!forceRefresh && isValid(current)) {
            return current.token;
        }
        CachedToken refreshed = tokenCache.compute(tenantId, (tid, existing) -> {
            if (!forceRefresh && isValid(existing)) {
                return existing;
            }
            TokenFetch fetch = fetchToken(tid);
            long expireAt = System.currentTimeMillis()
                    + Math.max(60, fetch.expiresInSeconds() - TOKEN_SAFETY_MARGIN_SECONDS) * 1000L;
            return new CachedToken(fetch.token(), expireAt);
        });
        log.info("IM渠道token已刷新: channel={}, tenantId={}", channel(), tenantId);
        return refreshed.token;
    }

    /**
     * 平台 errcode 是否为 token 失效族（需强刷重试）
     */
    protected static boolean isTokenExpired(int errcode) {
        return errcode == 40014 || errcode == 42001 || errcode == 41001;
    }

    private static boolean isValid(CachedToken cached) {
        return cached != null && System.currentTimeMillis() < cached.expireAtMillis;
    }

    /**
     * 实际获取 token（网络调用），返回 token 与有效期（秒）
     */
    protected abstract TokenFetch fetchToken(Long tenantId);

    private record CachedToken(String token, long expireAtMillis) {
    }

    /**
     * public 修饰以便不同包的渠道子类构造
     */
    public record TokenFetch(String token, long expiresInSeconds) {
    }
}
