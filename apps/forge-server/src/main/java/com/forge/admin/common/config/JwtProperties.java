package com.forge.admin.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * JWT 配置属性
 *
 * @author standadmin
 */
@Data
@Component
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {

    /**
     * 密钥
     */
    private String secret;

    /**
     * 过期时间（毫秒）
     */
    private Long expiration;

    /**
     * Refresh Token 过期时间（毫秒）
     */
    private Long refreshExpiration;

    /**
     * 请求头名称
     */
    private String header;

    /**
     * Token 前缀
     */
    private String prefix;
}
