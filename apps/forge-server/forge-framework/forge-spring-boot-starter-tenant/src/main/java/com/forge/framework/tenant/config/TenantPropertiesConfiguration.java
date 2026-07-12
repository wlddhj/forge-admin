package com.forge.framework.tenant.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 始终注册 TenantProperties（无论 enable=true/false）
 *
 * 之所以独立一个 Configuration 类，是因为 TenantAutoConfiguration 上加了
 * @ConditionalOnProperty(enable=true)，会导致 enable=false 时 TenantProperties 也消失，
 * 进而其他模块（如 AuthController 注入 TenantProperties 判断模式）无法装配。
 */
@Configuration
@EnableConfigurationProperties(TenantProperties.class)
public class TenantPropertiesConfiguration {
}
