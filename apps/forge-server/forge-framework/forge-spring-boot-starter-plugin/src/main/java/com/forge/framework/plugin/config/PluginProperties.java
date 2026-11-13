package com.forge.framework.plugin.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 插件契约层配置。
 *
 * @author forge-admin
 */
@Data
@ConfigurationProperties(prefix = "forge.plugin.license")
public class PluginProperties {

    /**
     * License 验签公钥（Base64 编码的 X.509 SubjectPublicKeyInfo）。
     * 未配置时回退 classpath:plugin-license-public.key。
     */
    private String publicKey;
}
