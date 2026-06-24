package com.forge.framework.redis.lock;

import com.forge.framework.redis.lock.RedisDistributedLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * 分布式锁自动配置
 *
 * 在 Redis 环境下自动注册分布式锁实现
 *
 * @author forge-admin
 */
@Configuration
@ConditionalOnClass(StringRedisTemplate.class)
public class DistributedLockAutoConfiguration {

    /**
     * 注册 Redis 分布式锁实现
     */
    @Bean
    @ConditionalOnMissingBean
    public RedisDistributedLock redisDistributedLock(StringRedisTemplate stringRedisTemplate) {
        return new RedisDistributedLock(stringRedisTemplate);
    }
}