package com.forge.modules.ai.client;

import com.forge.framework.localcache.LocalCaches;
import com.forge.modules.ai.config.AiModuleConfig;
import com.forge.modules.ai.entity.AiModelConfig;
import com.google.common.cache.Cache;
import com.openai.client.OpenAIClient;
import com.openai.client.OpenAIClientAsync;
import io.micrometer.observation.ObservationRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.http.okhttp.OpenAiHttpClientBuilderCustomizer;
import org.springframework.ai.openai.setup.OpenAiSetup;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 按 ai_model_config 编程式构建并缓存 OpenAiChatModel（配置变更指纹失效重建）
 *
 * <p>Spring AI 2.0：OpenAiApi（RestClient 封装）已移除，改为官方 OpenAI Java SDK 客户端
 * （OpenAIClient/OpenAIClientAsync，OkHttp 传输），经 OpenAiSetup 统一装配。
 */
@Slf4j
@Component
public class OpenAiChatModelFactory {

    private static final String DEFAULT_COMPLETIONS_PATH = "/chat/completions";

    private static final tools.jackson.databind.ObjectMapper OBJECT_MAPPER =
            new tools.jackson.databind.ObjectMapper();

    /**
     * 非流式请求 body 注入 thinking.type=disabled（智谱 GLM-4.x 等思考类模型关闭思考，
     * 防止思考耗尽 max_tokens 导致 content 为空）。Spring AI 2.0 走 OkHttp，平移为网络层拦截器。
     */
    private static final okhttp3.Interceptor THINKING_DISABLED_INTERCEPTOR = chain -> {
        okhttp3.Request request = chain.request();
        okhttp3.RequestBody body = request.body();
        if (body != null) {
            try {
                okio.Buffer buffer = new okio.Buffer();
                body.writeTo(buffer);
                byte[] raw = buffer.readByteArray();
                if (raw.length > 0) {
                    Map<String, Object> payload = OBJECT_MAPPER.readValue(raw, Map.class);
                    payload.put("thinking", Map.of("type", "disabled"));
                    byte[] rewritten = OBJECT_MAPPER.writeValueAsBytes(payload);
                    okhttp3.Request newRequest = request.newBuilder()
                            .method(request.method(), okhttp3.RequestBody.create(body.contentType(), rewritten))
                            .build();
                    return chain.proceed(newRequest);
                }
            } catch (Exception e) {
                log.warn("注入 thinking.type=disabled 失败，按原始请求发送: {}", e.getMessage());
            }
        }
        return chain.proceed(request);
    };

    private static final OpenAiHttpClientBuilderCustomizer THINKING_DISABLED_CUSTOMIZER =
            builder -> builder.interceptor(THINKING_DISABLED_INTERCEPTOR);

    /** 官方 SDK 内建重试次数（替代旧自建 RetryTemplate：2 次 × 3s 退避） */
    private static final int SDK_MAX_RETRIES = 2;

    private final AiModuleConfig aiModuleConfig;
    private final Cache<Long, CachedModel> cache;

    private record CachedModel(String fingerprint, OpenAiChatModel model) {
    }

    public OpenAiChatModelFactory(AiModuleConfig aiModuleConfig,
                                  LocalCaches localCaches) {
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
        Duration timeout = Duration.ofMillis(aiModuleConfig.getClient().getReadTimeout());
        List<OpenAiHttpClientBuilderCustomizer> customizers =
                Integer.valueOf(1).equals(config.getThinkingDisabled())
                        ? List.of(THINKING_DISABLED_CUSTOMIZER)
                        : List.of();

        OpenAIClient syncClient = OpenAiSetup.setupSyncClient(
                normalizeBaseUrl(config.getApiEndpoint()), config.getApiKey(),
                null, null, null, null, false, false, null,
                timeout, SDK_MAX_RETRIES, null, null,
                ObservationRegistry.NOOP, null, customizers);
        OpenAIClientAsync asyncClient = OpenAiSetup.setupAsyncClient(
                normalizeBaseUrl(config.getApiEndpoint()), config.getApiKey(),
                null, null, null, null, false, false, null,
                timeout, SDK_MAX_RETRIES, null, null,
                ObservationRegistry.NOOP, null, customizers);

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
                .openAiClient(syncClient)
                .openAiClientAsync(asyncClient)
                .options(options.build())
                .build();
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
