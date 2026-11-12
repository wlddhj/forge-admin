package com.forge.framework.redis.lock;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.List;
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

    /**
     * 原子释放脚本：仅当锁值等于本持有者令牌才删除。
     * 旧实现 unlock 只校验共享前缀——持锁超过锁时长后（锁已自动过期、他人持新锁），
     * 旧持有者 finally 释放会误删他人锁，导致第三方并发执行同一批任务（重复自动办理/提醒）。
     */
    private static final RedisScript<Long> UNLOCK_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
            Long.class);

    /**
     * 本线程持有的锁令牌栈（tryLock 入栈、unlock 出栈；同线程嵌套多把锁按 LIFO 配对）
     */
    private static final ThreadLocal<ArrayDeque<String>> TOKEN_STACK = new ThreadLocal<>();

    @Override
    public boolean tryLock(String lockKey, Duration timeout) {
        String key = LOCK_PREFIX + lockKey;
        String value = generateLockValue();

        try {
            Boolean success = redisTemplate.opsForValue()
                    .setIfAbsent(key, value, timeout);

            if (Boolean.TRUE.equals(success)) {
                ArrayDeque<String> stack = TOKEN_STACK.get();
                if (stack == null) {
                    stack = new ArrayDeque<>();
                    TOKEN_STACK.set(stack);
                }
                stack.offerLast(value);
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

        ArrayDeque<String> stack = TOKEN_STACK.get();
        String token = stack == null ? null : stack.pollLast();
        if (stack != null && stack.isEmpty()) {
            TOKEN_STACK.remove();
        }
        if (token == null) {
            log.warn("释放分布式锁失败: 本线程未持有锁令牌（非配对 unlock）: key={}", key);
            return;
        }

        try {
            Long removed = redisTemplate.execute(UNLOCK_SCRIPT, List.of(key), token);
            if (removed != null && removed == 1L) {
                log.debug("释放分布式锁成功: key={}", key);
            } else {
                log.warn("锁已过期被他人持有或令牌不匹配，跳过释放: key={}", key);
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