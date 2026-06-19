package com.forge.modules.system.auth.service;

import com.forge.modules.system.auth.properties.LoginPolicyProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * 登录失败计数与账户锁定服务。
 * - 失败计数 Redis key：login_fail:{username}，TTL = lockMinutes
 * - 锁定标记 Redis key：login_lock:{username}，TTL = lockMinutes
 * - 阶梯锁定策略：到达阈值后即锁定 lockMinutes 分钟；连续多次锁定可由管理员手动解锁
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LoginAttemptService {

    private final StringRedisTemplate redisTemplate;
    private final LoginPolicyProperties properties;

    public boolean isLocked(String username) {
        String lockKey = properties.getLockPrefix() + username;
        return Boolean.TRUE.equals(redisTemplate.hasKey(lockKey));
    }

    public long getRemainingLockSeconds(String username) {
        String lockKey = properties.getLockPrefix() + username;
        Long ttl = redisTemplate.getExpire(lockKey, TimeUnit.SECONDS);
        return ttl == null ? 0 : Math.max(0, ttl);
    }

    public void recordFailure(String username) {
        String failKey = properties.getFailCountPrefix() + username;
        String current = redisTemplate.opsForValue().get(failKey);
        int count = (current == null) ? 1 : Integer.parseInt(current) + 1;

        if (count >= properties.getMaxFailCount()) {
            // 触发锁定
            String lockKey = properties.getLockPrefix() + username;
            redisTemplate.opsForValue().set(lockKey, String.valueOf(System.currentTimeMillis()),
                    properties.getLockMinutes(), TimeUnit.MINUTES);
            // 清除失败计数（重新计时）
            redisTemplate.delete(failKey);
            log.warn("用户 {} 连续登录失败 {} 次，账号已锁定 {} 分钟", username, count, properties.getLockMinutes());
        } else {
            redisTemplate.opsForValue().set(failKey, String.valueOf(count),
                    properties.getLockMinutes(), TimeUnit.MINUTES);
            log.info("用户 {} 登录失败，当前失败次数 {}/{}", username, count, properties.getMaxFailCount());
        }
    }

    public void recordSuccess(String username) {
        redisTemplate.delete(properties.getFailCountPrefix() + username);
    }

    public void unlock(String username) {
        redisTemplate.delete(properties.getLockPrefix() + username);
        redisTemplate.delete(properties.getFailCountPrefix() + username);
    }
}
