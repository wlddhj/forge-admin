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
     * 连接池设空闲淘汰：上游 LB 常提前关闭空闲连接，复用旧连接会立即 ReadTimeout（高频"超时"根因）
     */
    @Bean("llmWebClientBuilder")
    public WebClient.Builder llmWebClientBuilder() {
        reactor.netty.resources.ConnectionProvider provider = reactor.netty.resources.ConnectionProvider.builder("llm")
                .maxConnections(100)
                .pendingAcquireMaxCount(500)
                .maxIdleTime(Duration.ofSeconds(20))
                .maxLifeTime(Duration.ofMinutes(5))
                .evictInBackground(Duration.ofSeconds(10))
                .build();
        HttpClient httpClient = HttpClient.create(provider)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, aiModuleConfig.getClient().getConnectTimeout())
                .responseTimeout(Duration.ofMillis(aiModuleConfig.getClient().getReadTimeout()));
        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(16 * 1024 * 1024));
    }

    /**
     * LLM 非流式调用 RestClient Builder（OpenAiApi.chatCompletionEntity 走 RestClient，此前未配置超时）
     */
    @Bean("llmRestClientBuilder")
    public org.springframework.web.client.RestClient.Builder llmRestClientBuilder() {
        org.springframework.http.client.SimpleClientHttpRequestFactory factory =
                new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(aiModuleConfig.getClient().getConnectTimeout());
        factory.setReadTimeout(aiModuleConfig.getClient().getReadTimeout());
        return org.springframework.web.client.RestClient.builder().requestFactory(factory);
    }

    /**
     * 通用 RestTemplate（system 模块 AppAuthService 等使用）
     */
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
