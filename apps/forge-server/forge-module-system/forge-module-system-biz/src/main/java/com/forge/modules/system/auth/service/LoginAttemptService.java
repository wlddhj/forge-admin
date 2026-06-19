package com.forge.modules.system.auth.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.forge.modules.system.auth.properties.LoginPolicyProperties;
import com.forge.modules.system.entity.SysUser;
import com.forge.modules.system.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

/**
 * 登录失败计数与账户锁定服务。
 * - 失败计数 Redis key：login_fail:{username}，TTL = lockMinutes
 * - 锁定标记 Redis key：login_lock:{username}，TTL = lockMinutes
 * - 同步更新 sys_user 表的 password_error_count / lock_time 字段，便于审计
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LoginAttemptService {

    private final StringRedisTemplate redisTemplate;
    private final LoginPolicyProperties properties;

    @Lazy
    @Autowired
    private SysUserMapper sysUserMapper;

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

            // 同步更新数据库：设置 lock_time 和 password_error_count
            LocalDateTime lockUntil = LocalDateTime.now().plusMinutes(properties.getLockMinutes());
            sysUserMapper.update(null, new LambdaUpdateWrapper<SysUser>()
                    .eq(SysUser::getUsername, username)
                    .set(SysUser::getLockTime, lockUntil)
                    .set(SysUser::getPasswordErrorCount, count));

            log.warn("用户 {} 连续登录失败 {} 次，账号已锁定 {} 分钟（截止 {}）",
                    username, count, properties.getLockMinutes(), lockUntil);
        } else {
            redisTemplate.opsForValue().set(failKey, String.valueOf(count),
                    properties.getLockMinutes(), TimeUnit.MINUTES);

            // 同步更新数据库：累加 password_error_count
            sysUserMapper.update(null, new LambdaUpdateWrapper<SysUser>()
                    .eq(SysUser::getUsername, username)
                    .set(SysUser::getPasswordErrorCount, count));

            log.info("用户 {} 登录失败，当前失败次数 {}/{}", username, count, properties.getMaxFailCount());
        }
    }

    public void recordSuccess(String username) {
        redisTemplate.delete(properties.getFailCountPrefix() + username);

        // 同步清除数据库的失败计数（lock_time 保留作为历史记录，由 unlock 清除）
        sysUserMapper.update(null, new LambdaUpdateWrapper<SysUser>()
                .eq(SysUser::getUsername, username)
                .set(SysUser::getPasswordErrorCount, 0));
    }

    public void unlock(String username) {
        redisTemplate.delete(properties.getLockPrefix() + username);
        redisTemplate.delete(properties.getFailCountPrefix() + username);

        // 同步清除数据库的锁定标记和失败计数
        sysUserMapper.update(null, new LambdaUpdateWrapper<SysUser>()
                .eq(SysUser::getUsername, username)
                .set(SysUser::getLockTime, null)
                .set(SysUser::getPasswordErrorCount, 0));
    }
}
