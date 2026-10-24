package com.forge.modules.ai.agent.graph;

import com.forge.common.exception.BusinessException;
import com.forge.framework.localcache.LocalCaches;
import com.forge.modules.ai.agent.AgentEvents;
import com.forge.modules.ai.agent.AgentProperties;
import com.forge.modules.ai.agent.kernel.AgentGraphContext;
import com.forge.modules.ai.agent.kernel.AgentGraphState;
import com.forge.modules.ai.client.LlmClient;
import com.forge.modules.ai.dto.request.ChatRequest;
import com.forge.modules.ai.dto.response.ChatResponse;
import com.forge.modules.ai.entity.AiModelConfig;
import com.google.common.cache.Cache;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/**
 * 图片理解节点（单 Agent 图与团队协作图共用）：视觉模型识别为文字注入 IMAGE_TEXT。
 * 识别结果按「整批图片引用哈希+模型 ID」缓存（TTL 可配，0=禁用）——追问场景同图列表不变时
 * 跳过视觉调用（整条链路最慢单点），增删图片或换模型视为新批次。
 */
@Slf4j
@Component
public class MultimodalSupport {

    public static final String NODE_MULTIMODAL = "multimodal";

    private final LlmClient llmClient;
    private final DecisionJsonParser decisionJsonParser;
    private final AgentProperties agentProperties;
    private final Cache<String, String> recognitionCache;
    private final com.forge.modules.ai.config.AiMetrics aiMetrics;

    public MultimodalSupport(LlmClient llmClient, DecisionJsonParser decisionJsonParser,
                             AgentProperties agentProperties, LocalCaches localCaches,
                             com.forge.modules.ai.config.AiMetrics aiMetrics) {
        this.llmClient = llmClient;
        this.decisionJsonParser = decisionJsonParser;
        this.agentProperties = agentProperties;
        this.aiMetrics = aiMetrics;
        int ttlMinutes = Math.max(1, agentProperties.getImageCacheTtlMinutes());
        this.recognitionCache = localCaches.get("ai-image-recognition", b -> b
                .maximumSize(500)
                .expireAfterWrite(Duration.ofMinutes(ttlMinutes)));
    }

    public AsyncNodeAction<AgentGraphState> node(AgentGraphContext ctx) {
        return AsyncNodeAction.node_async(state -> {
            List<String> images = state.value(AgentGraphState.IMAGES)
                    .map(v -> (List<String>) v).orElse(List.of());
            if (images.isEmpty()) {
                return Map.of();
            }
            AiModelConfig model = ctx.getNodeModels().get(NODE_MULTIMODAL);
            int seq = ctx.nextSeq();
            if (model == null) {
                ctx.emit(AgentEvents.Type.STEP_END, NODE_MULTIMODAL, "图片理解", Map.of(
                        "seq", seq, "status", "SKIPPED", "output", "未配置多模态模型(model_type=multimodal)，已跳过图片理解"));
                return Map.of(AgentGraphState.IMAGE_TEXT, "（未配置视觉模型，无法识别图片）");
            }
            long start = System.currentTimeMillis();
            ctx.emit(AgentEvents.Type.STEP_START, NODE_MULTIMODAL, "图片理解", Map.of(
                    "seq", seq, "model", model.getModelName(), "images", images.size()));
            // 整批缓存命中：跳过视觉调用（追问场景同图列表不变）
            String cacheKey = cacheKey(images, model);
            String cached = cacheEnabled() ? recognitionCache.getIfPresent(cacheKey) : null;
            if (cached != null) {
                aiMetrics.cacheResult("image", true);
                ctx.emit(AgentEvents.Type.STEP_END, NODE_MULTIMODAL, "图片理解", Map.of(
                        "seq", seq, "status", "SUCCESS", "cacheHit", true,
                        "output", decisionJsonParser.truncate(cached),
                        "durationMs", System.currentTimeMillis() - start,
                        "modelId", model.getId()));
                return Map.of(AgentGraphState.IMAGE_TEXT, cached);
            }
            aiMetrics.cacheResult("image", false);
            ChatRequest request = new ChatRequest();
            request.setContent("请识别并详细描述图片内容；若图片包含文字，请完整提取（OCR）。");
            request.setImages(images);
            request.setTemperature(0.2);
            ChatResponse response = llmClient.chat(request, model);
            if (!Boolean.TRUE.equals(response.getSuccess())) {
                throw new BusinessException("图片理解失败: " + response.getErrorMessage());
            }
            String imageText = response.getContent() == null ? "" : response.getContent();
            if (cacheEnabled()) {
                try {
                    recognitionCache.put(cacheKey, imageText);
                } catch (Exception e) {
                    log.debug("图片识别缓存写入失败: {}", e.getMessage());
                }
            }
            ctx.emit(AgentEvents.Type.STEP_END, NODE_MULTIMODAL, "图片理解", Map.of(
                    "seq", seq, "status", "SUCCESS",
                    "output", decisionJsonParser.truncate(imageText),
                    "tokensIn", response.getInputTokens() == null ? 0 : response.getInputTokens(),
                    "tokensOut", response.getOutputTokens() == null ? 0 : response.getOutputTokens(),
                    "durationMs", System.currentTimeMillis() - start,
                    "modelId", model.getId()));
            return Map.of(AgentGraphState.IMAGE_TEXT, imageText);
        });
    }

    private boolean cacheEnabled() {
        return agentProperties.getImageCacheTtlMinutes() > 0;
    }

    /** 整批图片引用哈希 + 模型 ID（引用串覆盖 URL/base64 两形态） */
    private String cacheKey(List<String> images, AiModelConfig model) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (String image : images) {
                digest.update(String.valueOf(image).getBytes(StandardCharsets.UTF_8));
                digest.update((byte) 0);
            }
            return HexFormat.of().formatHex(digest.digest()) + "|" + model.getId();
        } catch (Exception e) {
            return String.join("\u0000", images) + "|" + model.getId();
        }
    }
}
