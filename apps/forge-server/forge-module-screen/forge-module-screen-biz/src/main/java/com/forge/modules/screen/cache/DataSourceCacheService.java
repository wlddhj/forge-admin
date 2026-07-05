package com.forge.modules.screen.cache;

import com.forge.modules.screen.constant.ScreenConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * 数据源结果缓存服务。
 *
 * <p>
 * 提供：
 * <ul>
 * <li>{@link #getOrLoad(String, int, Supplier)}：Redis 缓存查询，未命中时通过 single-flight 锁
 * 避免 thundering herd；锁内 double-check 再次读 Redis，命中则直接返回，否则调 loader 并按
 * TTL 写回 Redis。</li>
 * <li>{@link #cacheKey(Long, String)}：基于 dataSourceId 与 paramsJson 计算稳定 SHA-256
 * （截取前 32 个 hex 字符 = 128 位）缓存 key，前缀 {@link ScreenConstants#CACHE_PREFIX}。</li>
 * </ul>
 * </p>
 *
 * <p>
 * 线程安全：单例 Spring Bean，{@code singleflightLock} 为实例字段，对所有数据源共享同一把
 * 锁。这意味着并发的不同 key 也会串行执行 loader，是性能上的轻微损失，但语义上安全。
 * 后续若需要更细粒度的并发，可改为按 key 分桶的锁映射。
 * </p>
 *
 * @author standadmin
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataSourceCacheService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ReentrantLock singleflightLock = new ReentrantLock();

    /**
     * 查询缓存，未命中时调 loader 取值并按 TTL 写回 Redis。
     *
     * <p>
     * ttlSeconds &le; 0 表示跳过缓存：直接调 loader 返回，不读不写 Redis。
     * </p>
     *
     * @param key        Redis key（建议通过 {@link #cacheKey(Long, String)} 生成）
     * @param ttlSeconds 缓存有效期（秒），&le; 0 表示不缓存
     * @param loader     缓存未命中时的取值回调
     * @param <T>        返回类型
     * @return 缓存值或 loader 返回值
     */
    @SuppressWarnings("unchecked")
    public <T> T getOrLoad(String key, int ttlSeconds, Supplier<T> loader) {
        if (ttlSeconds <= 0) {
            return loader.get();
        }
        Object cached = redisTemplate.opsForValue().get(key);
        if (cached != null) {
            return (T) cached;
        }

        singleflightLock.lock();
        try {
            // double-check：另一并发线程可能已经填充了缓存
            cached = redisTemplate.opsForValue().get(key);
            if (cached != null) {
                return (T) cached;
            }

            T fresh = loader.get();
            redisTemplate.opsForValue().set(key, fresh, Duration.ofSeconds(ttlSeconds));
            return fresh;
        } finally {
            singleflightLock.unlock();
        }
    }

    /**
     * 生成缓存 key：基于 dataSourceId 与 paramsJson 的 SHA-256（取前 32 个 hex 字符 = 128 位），
     * 前缀 {@link ScreenConstants#CACHE_PREFIX}。
     *
     * @param dataSourceId 数据源 ID
     * @param paramsJson   参数 JSON（可为 null，按空串处理）
     * @return 形如 {@code screen:ds:<32 hex>} 的 key
     */
    public String cacheKey(Long dataSourceId, String paramsJson) {
        String hash = sha256(dataSourceId + ":" + (paramsJson == null ? "" : paramsJson));
        return ScreenConstants.CACHE_PREFIX + hash;
    }

    private String sha256(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] h = md.digest(s.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(h).substring(0, 32);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
