package com.forge.admin.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * JustAuth 配置属性
 */
@Data
@Component
@ConfigurationProperties(prefix = "justauth")
public class JustAuthProperties {

    private Map<String, ProviderConfig> type;

    @Data
    public static class ProviderConfig {
        /** 客户端ID（微信AppID / 钉钉AppKey） */
        private String clientId;
        /** 客户端密钥（微信AppSecret / 钉钉AppSecret） */
        private String clientSecret;
        /** 回调地址 */
        private String redirectUri;
    }
}
