package com.forge.modules.ai.client;

import com.forge.framework.localcache.LocalCaches;
import com.forge.modules.ai.config.AiModuleConfig;
import com.forge.modules.ai.entity.AiModelConfig;
import com.google.common.cache.Cache;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;
import java.util.Objects;

/**
 * 按 ai_model_config 编程式构建并缓存 OpenAiChatModel（配置变更指纹失效重建）
 */
@Slf4j
@Component
public class OpenAiChatModelFactory {

    private static final String DEFAULT_COMPLETIONS_PATH = "/chat/completions";

    private static final com.fasterxml.jackson.databind.ObjectMapper OBJECT_MAPPER =
            new com.fasterxml.jackson.databind.ObjectMapper();

    /**
     * 非流式请求 body 注入 thinking.type=disabled（智谱 GLM-4.x 等思考类模型关闭思考，
     * 防止思考耗尽 max_tokens 导致 content 为空）。仅影响 OpenAiApi 的 RestClient 调用路径，流式 WebClient 不经过。
     */
    private static final org.springframework.http.client.ClientHttpRequestInterceptor THINKING_DISABLED_INTERCEPTOR =
            (request, body, execution) -> {
                if (body != null && body.length > 0) {
                    try {
                        Map<String, Object> payload = OBJECT_MAPPER.readValue(body, Map.class);
                        payload.put("thinking", Map.of("type", "disabled"));
                        return execution.execute(request, OBJECT_MAPPER.writeValueAsBytes(payload));
                    } catch (Exception e) {
                        log.warn("注入 thinking.type=disabled 失败，按原始请求发送: {}", e.getMessage());
                    }
                }
                return execution.execute(request, body);
            };

    private final WebClient.Builder webClientBuilder;
    private final org.springframework.web.client.RestClient.Builder restClientBuilder;
    private final AiModuleConfig aiModuleConfig;
    private final Cache<Long, CachedModel> cache;

    private record CachedModel(String fingerprint, OpenAiChatModel model) {
    }

    public OpenAiChatModelFactory(@Qualifier("llmWebClientBuilder") WebClient.Builder webClientBuilder,
                                  @Qualifier("llmRestClientBuilder") org.springframework.web.client.RestClient.Builder restClientBuilder,
                                  AiModuleConfig aiModuleConfig,
                                  LocalCaches localCaches) {
        this.webClientBuilder = webClientBuilder;
        this.restClientBuilder = restClientBuilder;
        this.aiModuleConfig = aiModuleConfig;
        // maximumSize 兜底：配置删除后遗留实例不致无限驻留
        this.cache = localCaches.get("ai-chat-models", b -> b.maximumSize(200));
    }

    /**
     * 获取（或构建）模型实例；配置指纹变化时重建（原子 compute 比对）
     */
    public OpenAiChatModel get(AiModelConfig config) {
        String fingerprint = fingerprint(config);
        CachedModel cached = cache.asMap().compute(config.getId(), (id, existing) ->
                existing != null && existing.fingerprint().equals(fingerprint)
                        ? existing
                        : new CachedModel(fingerprint, build(config)));
        return cached.model();
    }

    /**
     * 配置更新后主动失效
     */
    public void evict(Long modelConfigId) {
        cache.invalidate(modelConfigId);
    }

    private OpenAiChatModel build(AiModelConfig config) {
        org.springframework.web.client.RestClient.Builder restBuilder = restClientBuilder.clone();
        if (Integer.valueOf(1).equals(config.getThinkingDisabled())) {
            restBuilder.requestInterceptor(THINKING_DISABLED_INTERCEPTOR);
        }
        OpenAiApi api = OpenAiApi.builder()
                .baseUrl(normalizeBaseUrl(config.getApiEndpoint()))
                .apiKey(config.getApiKey())
                .completionsPath(DEFAULT_COMPLETIONS_PATH)
                .webClientBuilder(webClientBuilder.clone())
                .restClientBuilder(restBuilder)
                .build();

        OpenAiChatOptions.Builder options = OpenAiChatOptions.builder().model(config.getModelCode());
        Double temperature = resolveTemperature(config);
        if (temperature != null) {
            options.temperature(temperature);
        }
        Integer maxTokens = resolveMaxTokens(config);
        if (maxTokens != null) {
            options.maxTokens(maxTokens);
        }

        log.info("构建 LLM 模型实例: {} ({})", config.getModelName(), config.getProvider());
        return OpenAiChatModel.builder()
                .openAiApi(api)
                .defaultOptions(options.build())
                .retryTemplate(buildRetryTemplate())
                .build();
    }

    /**
     * 收紧重试：默认 3 次 × 10s 退避在长超时（120s）下最坏拖 6 分钟+；
     * 改为 2 次 × 3s 退避，仅对 5xx/IO 错误重试（ReadTimeout 重试一次即放弃并透出失败）
     */
    private org.springframework.retry.support.RetryTemplate buildRetryTemplate() {
        org.springframework.retry.backoff.FixedBackOffPolicy fixedBackOffPolicy = new org.springframework.retry.backoff.FixedBackOffPolicy();
        fixedBackOffPolicy.setBackOffPeriod(3000L);
        org.springframework.retry.support.RetryTemplate template = new org.springframework.retry.support.RetryTemplate();
        template.setBackOffPolicy(fixedBackOffPolicy);
        template.setRetryPolicy(new org.springframework.retry.policy.SimpleRetryPolicy(2));
        return template;
    }

    private Double resolveTemperature(AiModelConfig config) {
        if (config.getTemperature() != null) {
            return config.getTemperature().doubleValue();
        }
        return aiModuleConfig.getModels().getDefaultTemperature();
    }

    private Integer resolveMaxTokens(AiModelConfig config) {
        if (config.getMaxTokens() != null) {
            return config.getMaxTokens();
        }
        return aiModuleConfig.getModels().getDefaultMaxTokens();
    }

    private String fingerprint(AiModelConfig config) {
        return String.join("|",
                Objects.toString(config.getApiEndpoint(), ""),
                Objects.toString(config.getApiKey(), ""),
                Objects.toString(config.getModelCode(), ""),
                Objects.toString(resolveTemperature(config), ""),
                Objects.toString(resolveMaxTokens(config), ""),
                Objects.toString(config.getThinkingDisabled(), ""),
                Objects.toString(config.getUpdateTime(), ""));
    }

    /**
     * 兼容存量数据：api_endpoint 可能存了完整 completions URL，截去路径后缀只留 base-url
     */
    private String normalizeBaseUrl(String apiEndpoint) {
        String url = apiEndpoint == null ? "" : apiEndpoint.trim();
        if (url.endsWith(DEFAULT_COMPLETIONS_PATH)) {
            url = url.substring(0, url.length() - DEFAULT_COMPLETIONS_PATH.length());
        }
        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        return url;
    }
}
