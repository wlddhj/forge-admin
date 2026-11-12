package com.forge.framework.redis.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.cache.interceptor.KeyGenerator;
import org.springframework.cache.interceptor.SimpleCacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import tools.jackson.databind.DefaultTyping;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import tools.jackson.databind.jsontype.PolymorphicTypeValidator;

import org.springframework.beans.factory.annotation.Value;

import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
    private static final long USER_AUTH_CACHE_TTL = 300;       // 用户认证上下文缓存 5 分钟（变更钩子删缓存为主，TTL 兜底）

    /**
     * 默认不做租户前缀的缓存名（跨租户共享数据/共享表）：
     * dictData/dictType/sysConfig/menu/dept 为全局共享配置；userAuth 必须共享（见下方注释）；
     * fileConfig 对应 sys_file_config 共享表（在 tenant ignore-tables），前缀化会串出"首租户独占"
     */
    private static final Set<String> DEFAULT_IGNORE_CACHES =
            Set.of("dictData", "dictType", "sysConfig", "menu", "dept", "userAuth", "fileConfig");

    /**
     * Redis 缓存管理器
     * 使用专门的 ObjectMapper 配置类型信息，解决缓存反序列化问题
     */
    @Bean
    public CacheManager cacheManager(RedisConnectionFactory factory,
                                     @Value("${forge.tenant.ignore-caches:}") List<String> configuredIgnoreCaches) {
        // 创建专门用于 Redis 缓存的 Jackson 3 Mapper（java.time 内建，无需 JavaTimeModule）
        PolymorphicTypeValidator ptv = BasicPolymorphicTypeValidator.builder()
                .allowIfBaseType(Object.class)
                .build();
        ObjectMapper redisObjectMapper = JsonMapper.builder()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
                // Jackson 3 默认即按 ISO 字符串写日期（等价旧的 WRITE_DATES_AS_TIMESTAMPS=false）
                .changeDefaultPropertyInclusion(value -> value.withValueInclusion(JsonInclude.Include.NON_NULL))
                // 启用默认类型信息，允许反序列化为正确类型
                .activateDefaultTyping(ptv, DefaultTyping.NON_FINAL)
                .build();

        // 使用专门的 ObjectMapper 创建序列化器（Jackson 3 线）
        GenericJacksonJsonRedisSerializer jsonSerializer = new GenericJacksonJsonRedisSerializer(redisObjectMapper);
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

        // 用户认证上下文缓存（JWT 过滤器认证路径）
        // 必须留在 ignoreCaches：租户前缀由 TenantRedisCacheManager 在缓存首次创建时从
        // TenantContextHolder 烘焙，而过滤器执行中途会切换租户上下文，走名前缀会跨租户串缓存；
        // 租户段由 UserAuthCacheService 显式写入 key
        cacheConfigurations.put("userAuth", defaultConfig.entryTtl(Duration.ofSeconds(USER_AUTH_CACHE_TTL)));

        return new TenantRedisCacheManager(
                RedisCacheWriter.lockingRedisCacheWriter(factory),
                defaultConfig,
                resolveIgnoreCaches(configuredIgnoreCaches),
                cacheConfigurations  // per-cache TTL 配置
        );
    }

    /**
     * ignoreCaches = 默认共享清单 ∪ yml 配置（forge.tenant.ignore-caches，逗号分隔可增配）
     */
    static Set<String> resolveIgnoreCaches(List<String> configuredIgnoreCaches) {
        Set<String> ignoreCaches = new HashSet<>(DEFAULT_IGNORE_CACHES);
        configuredIgnoreCaches.stream()
                .filter(name -> !name.isBlank())
                .map(String::trim)
                .forEach(ignoreCaches::add);
        return ignoreCaches;
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
