package com.forge.framework.redis.lock;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Redis 分布式锁实现
 *
 * 特性：
 * - 基于 Redis SETNX 实现
 * - 支持自动过期防止死锁
 * - 支持锁重入检查
 * - 支持泛型 executeWithLock 方法
 *
 * 使用示例：
 * <pre>
 * // 简单加锁
 * if (lock.tryLock("my-lock", Duration.ofMinutes(5))) {
 *     try {
 *         // 执行业务逻辑
 *     } finally {
 *         lock.unlock("my-lock");
 *     }
 * }
 *
 * // 带任务执行（自动释放锁）
 * String result = lock.executeWithLock("my-lock", Duration.ofMinutes(5), () -> {
 *     return doSomething();
 * });
 *
 * // Runnable 任务
 * boolean success = lock.runWithLock("my-lock", Duration.ofMinutes(5), () -> {
 *     doSomething();
 * });
 * </pre>
 *
 * @author forge-admin
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisDistributedLock implements DistributedLock {

    private final StringRedisTemplate redisTemplate;

    /**
     * 锁前缀
     */
    private static final String LOCK_PREFIX = "forge:lock:";

    /**
     * 锁值前缀（用于标识锁的持有者）
     */
    private static final String LOCK_VALUE_PREFIX = "forge-admin:";

    @Override
    public boolean tryLock(String lockKey, Duration timeout) {
        String key = LOCK_PREFIX + lockKey;
        String value = generateLockValue();

        try {
            Boolean success = redisTemplate.opsForValue()
                    .setIfAbsent(key, value, timeout);

            if (Boolean.TRUE.equals(success)) {
                log.debug("获取分布式锁成功: key={}, value={}, timeout={}", key, value, timeout);
                return true;
            }

            log.debug("获取分布式锁失败（已被占用）: key={}", key);
            return false;
        } catch (Exception e) {
            log.error("获取分布式锁异常: key={}, error={}", key, e.getMessage());
            return false;
        }
    }

    @Override
    public void unlock(String lockKey) {
        String key = LOCK_PREFIX + lockKey;

        try {
            // 检查锁是否属于当前实例
            String value = redisTemplate.opsForValue().get(key);
            if (value != null && value.startsWith(LOCK_VALUE_PREFIX)) {
                redisTemplate.delete(key);
                log.debug("释放分布式锁成功: key={}", key);
            } else if (value != null) {
                log.warn("锁不属于当前实例，跳过释放: key={}, actualValue={}", key, value);
            } else {
                log.debug("锁已不存在，无需释放: key={}", key);
            }
        } catch (Exception e) {
            log.error("释放分布式锁异常: key={}, error={}", key, e.getMessage());
        }
    }

    @Override
    public boolean isLocked(String lockKey) {
        String key = LOCK_PREFIX + lockKey;

        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(key));
        } catch (Exception e) {
            log.error("检查锁状态异常: key={}, error={}", key, e.getMessage());
            return false;
        }
    }

    /**
     * 获取锁的剩余过期时间
     *
     * @param lockKey 锁标识
     * @return 剩余过期时间（毫秒），-2 表示锁不存在，-1 表示未设置过期时间
     */
    public long getRemainingTime(String lockKey) {
        String key = LOCK_PREFIX + lockKey;
        try {
            Long ttl = redisTemplate.getExpire(key, TimeUnit.MILLISECONDS);
            return ttl != null ? ttl : -2;
        } catch (Exception e) {
            log.error("获取锁剩余时间异常: key={}, error={}", key, e.getMessage());
            return -2;
        }
    }

    /**
     * 强制释放锁（不检查持有者）
     * 仅用于特殊场景，如锁超时后的清理
     *
     * @param lockKey 锁标识
     */
    public void forceUnlock(String lockKey) {
        String key = LOCK_PREFIX + lockKey;
        try {
            redisTemplate.delete(key);
            log.warn("强制释放分布式锁: key={}", key);
        } catch (Exception e) {
            log.error("强制释放锁异常: key={}, error={}", key, e.getMessage());
        }
    }

    /**
     * 生成锁的唯一值
     */
    private String generateLockValue() {
        return LOCK_VALUE_PREFIX + UUID.randomUUID().toString();
    }
}