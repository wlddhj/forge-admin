package com.forge.framework.plugin.config;

import com.forge.framework.plugin.guard.PluginGuardAspect;
import com.forge.framework.plugin.guard.PluginStateResolver;
import com.forge.framework.plugin.license.LicenseVerifier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StreamUtils;

import java.nio.charset.StandardCharsets;

/**
 * 插件契约层自动装配：验签器 + 请求闸切面。
 *
 * <p>PluginStateResolver / PluginMenuFilter / PluginDataApi 的实现 Bean
 * 由插件管理业务模块（forge-module-plugin）按需提供，本层只消费。
 *
 * @author forge-admin
 */
@Slf4j
@AutoConfiguration
@EnableConfigurationProperties(PluginProperties.class)
public class PluginAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public LicenseVerifier licenseVerifier(PluginProperties properties) {
        String publicKey = properties.getPublicKey();
        if (publicKey == null || publicKey.isBlank()) {
            publicKey = loadDefaultPublicKey();
        }
        return new LicenseVerifier(publicKey);
    }

    @Bean
    @ConditionalOnMissingBean
    public PluginGuardAspect pluginGuardAspect(ObjectProvider<PluginStateResolver> resolverProvider) {
        return new PluginGuardAspect(resolverProvider);
    }

    private String loadDefaultPublicKey() {
        ClassPathResource resource = new ClassPathResource("plugin-license-public.key");
        if (!resource.exists()) {
            // 装配期容忍缺失（依赖方上下文不炸）：License 导入使用期才 fail-fast
            log.warn("未配置 forge.plugin.license.public-key 且 classpath 缺少 plugin-license-public.key，License 导入功能不可用（其余插件功能不受影响）");
            return null;
        }
        try {
            return StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8).trim();
        } catch (Exception e) {
            log.warn("插件 License 公钥资源读取失败，License 导入功能不可用", e);
            return null;
        }
    }
}
