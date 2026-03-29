package com.forge.admin.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.cache.interceptor.KeyGenerator;
import org.springframework.cache.interceptor.SimpleCacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * 缓存配置
 *
 * @author standadmin
 */
@Configuration
// 临时禁用缓存来排查问题
@EnableCaching
public class CacheConfig implements CachingConfigurer {

    /**
     * 缓存过期时间配置（秒）
     */
    private static final long DICT_DATA_CACHE_TTL = 3600;      // 字典数据缓存 1 小时
    private static final long DICT_TYPE_CACHE_TTL = 7200;      // 字典类型缓存 2 小时
    private static final long CONFIG_CACHE_TTL = 1800;         // 系统配置缓存 30 分钟
    private static final long USER_INFO_CACHE_TTL = 1800;      // 用户信息缓存 30 分钟
    private static final long MENU_CACHE_TTL = 3600;           // 菜单缓存 1 小时
    private static final long DEPT_CACHE_TTL = 3600;            // 部门缓存 1 小时

    /**
     * Redis 缓存管理器
     * 使用注入的 ObjectMapper，已配置自定义序列化器
     */
    @Bean
    public CacheManager cacheManager(RedisConnectionFactory factory, ObjectMapper objectMapper) {
        // 使用注入的 ObjectMapper 创建序列化器
        GenericJackson2JsonRedisSerializer jsonSerializer = new GenericJackson2JsonRedisSerializer(objectMapper);
        StringRedisSerializer stringSerializer = new StringRedisSerializer();

        // 默认缓存配置（30分钟）
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofSeconds(1800))
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(stringSerializer))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(jsonSerializer));

        // 配置不同缓存名称的过期时间
        Map<String, RedisCacheConfiguration> cacheConfigurations = new HashMap<>();

        // 字典数据缓存
        cacheConfigurations.put("dictData", defaultConfig.entryTtl(Duration.ofSeconds(DICT_DATA_CACHE_TTL)));
        cacheConfigurations.put("dictType", defaultConfig.entryTtl(Duration.ofSeconds(DICT_TYPE_CACHE_TTL)));

        // 系统配置缓存
        cacheConfigurations.put("sysConfig", defaultConfig.entryTtl(Duration.ofSeconds(CONFIG_CACHE_TTL)));

        // 用户信息缓存
        cacheConfigurations.put("userInfo", defaultConfig.entryTtl(Duration.ofSeconds(USER_INFO_CACHE_TTL)));

        // 菜单缓存
        cacheConfigurations.put("menu", defaultConfig.entryTtl(Duration.ofSeconds(MENU_CACHE_TTL)));
        cacheConfigurations.put("dept", defaultConfig.entryTtl(Duration.ofSeconds(DEPT_CACHE_TTL)));

        return RedisCacheManager.builder(factory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(cacheConfigurations)
                .transactionAware()
                .build();
    }

    /**
     * 自定义缓存 key 生成器
     * 格式：类名:方法名:参数值
     */
    @Bean
    @Override
    public KeyGenerator keyGenerator() {
        return (target, method, params) -> {
            StringBuilder sb = new StringBuilder();
            sb.append(target.getClass().getSimpleName());
            sb.append(":");
            sb.append(method.getName());
            if (params.length > 0) {
                sb.append(":");
                for (Object param : params) {
                    if (param != null) {
                        sb.append(param.toString());
                    }
                }
            }
            return sb.toString();
        };
    }

    /**
     * 缓存异常处理器
     * 缓存异常不影响业务流程
     */
    @Bean
    @Override
    public CacheErrorHandler errorHandler() {
        return new SimpleCacheErrorHandler();
    }
}
