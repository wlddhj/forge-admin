package com.forge.admin.common.config;

import me.zhyd.oauth.request.AuthDingTalkRequest;
import me.zhyd.oauth.request.AuthRequest;
import me.zhyd.oauth.request.AuthWeChatOpenRequest;
import me.zhyd.oauth.config.AuthConfig;
import me.zhyd.oauth.cache.AuthStateCache;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * JustAuth 配置
 * 手动构建各平台的 AuthRequest Bean，避免 starter 自动配置冲突
 */
@Configuration
public class JustAuthConfig {

    @Bean
    public AuthStateCache authStateCache(StringRedisTemplate stringRedisTemplate) {
        return new RedisStateCache(stringRedisTemplate);
    }

    @Bean
    public Map<String, AuthRequest> authRequestMap(JustAuthProperties properties, AuthStateCache stateCache) {
        Map<String, AuthRequest> map = new HashMap<>();

        if (properties.getType() == null) {
            return map;
        }

        // 微信开放平台
        JustAuthProperties.ProviderConfig wechatConfig = properties.getType().get("wechat");
        if (wechatConfig != null && StringUtils.hasText(wechatConfig.getClientId())) {
            map.put("wechat", new AuthWeChatOpenRequest(
                    AuthConfig.builder()
                            .clientId(wechatConfig.getClientId())
                            .clientSecret(wechatConfig.getClientSecret())
                            .redirectUri(wechatConfig.getRedirectUri())
                            .build(),
                    stateCache
            ));
        }

        // 钉钉
        JustAuthProperties.ProviderConfig dingtalkConfig = properties.getType().get("dingtalk");
        if (dingtalkConfig != null && StringUtils.hasText(dingtalkConfig.getClientId())) {
            map.put("dingtalk", new AuthDingTalkRequest(
                    AuthConfig.builder()
                            .clientId(dingtalkConfig.getClientId())
                            .clientSecret(dingtalkConfig.getClientSecret())
                            .redirectUri(dingtalkConfig.getRedirectUri())
                            .build(),
                    stateCache
            ));
        }

        return map;
    }
}
