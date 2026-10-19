package com.forge.framework.localcache;

import com.google.common.cache.CacheBuilder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 本地缓存自动配置：仅注册 LocalCaches 统一入口，
 * 不注册 CacheManager，不影响 Spring Cache（@Cacheable/Redis）体系
 */
@Configuration
@ConditionalOnClass(CacheBuilder.class)
public class LocalCacheAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public LocalCaches localCaches() {
        return new LocalCaches();
    }
}
