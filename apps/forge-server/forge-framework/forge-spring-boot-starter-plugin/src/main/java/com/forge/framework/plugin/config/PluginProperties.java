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

    /**
     * 在线签发私钥（Base64 编码的 PKCS#8，建议 jasypt ENC 加密存储）。
     * 配置后 License 管理页开放「在线签发」（自营姿势）；
     * 未配置仅导入（私有化交付姿势，私钥绝不进部署）。
     */
    private String privateKey;
}
