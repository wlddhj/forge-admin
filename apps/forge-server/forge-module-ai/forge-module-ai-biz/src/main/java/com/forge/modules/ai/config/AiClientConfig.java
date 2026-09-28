package com.forge.modules.ai.config;

import io.netty.channel.ChannelOption;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

/**
 * LLM 客户端配置：为上游 OpenAI 兼容端点提供带超时的 WebClient Builder
 */
@Configuration
@RequiredArgsConstructor
public class AiClientConfig {

    private final AiModuleConfig aiModuleConfig;

    /**
     * LLM 上游 WebClient Builder（连接/读超时来自 ai.client 配置，流式响应需要较大缓冲）
     */
    @Bean("llmWebClientBuilder")
    public WebClient.Builder llmWebClientBuilder() {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, aiModuleConfig.getClient().getConnectTimeout())
                .responseTimeout(Duration.ofMillis(aiModuleConfig.getClient().getReadTimeout()));
        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(16 * 1024 * 1024));
    }

    /**
     * 通用 RestTemplate（system 模块 AppAuthService 等使用）
     */
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
