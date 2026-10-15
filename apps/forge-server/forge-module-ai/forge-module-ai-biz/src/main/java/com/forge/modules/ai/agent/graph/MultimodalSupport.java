package com.forge.modules.ai.agent.graph;

import com.forge.common.exception.BusinessException;
import com.forge.modules.ai.agent.AgentEvents;
import com.forge.modules.ai.agent.kernel.AgentGraphContext;
import com.forge.modules.ai.agent.kernel.AgentGraphState;
import com.forge.modules.ai.client.LlmClient;
import com.forge.modules.ai.dto.request.ChatRequest;
import com.forge.modules.ai.dto.response.ChatResponse;
import com.forge.modules.ai.entity.AiModelConfig;
import lombok.RequiredArgsConstructor;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 图片理解节点（单 Agent 图与团队协作图共用）：视觉模型识别为文字注入 IMAGE_TEXT
 */
@Component
@RequiredArgsConstructor
public class MultimodalSupport {

    public static final String NODE_MULTIMODAL = "multimodal";

    private final LlmClient llmClient;
    private final DecisionJsonParser decisionJsonParser;

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
            ChatRequest request = new ChatRequest();
            request.setContent("请识别并详细描述图片内容；若图片包含文字，请完整提取（OCR）。");
            request.setImages(images);
            request.setTemperature(0.2);
            ChatResponse response = llmClient.chat(request, model);
            if (!Boolean.TRUE.equals(response.getSuccess())) {
                throw new BusinessException("图片理解失败: " + response.getErrorMessage());
            }
            String imageText = response.getContent() == null ? "" : response.getContent();
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
}
