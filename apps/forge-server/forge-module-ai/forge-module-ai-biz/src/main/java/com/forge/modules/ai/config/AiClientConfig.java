package com.forge.modules.ai.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * LLM 客户端配置
 *
 * <p>Spring AI 2.0 起模型传输由官方 OpenAI Java SDK 自带 OkHttp 承担
 * （超时/重试在 OpenAiChatModelFactory 内按 ai.client 配置传入），
 * 原 llmWebClientBuilder/llmRestClientBuilder 已随 OpenAiApi 一并移除。
 */
@Configuration
@RequiredArgsConstructor
public class AiClientConfig {

    /**
     * 通用 RestTemplate（system 模块 AppAuthService 等使用）
     */
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
