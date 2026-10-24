package com.forge.modules.ai.client;

import com.forge.modules.ai.dto.request.ChatRequest;
import com.forge.modules.ai.dto.response.ChatResponse;
import com.forge.modules.ai.entity.AiModelConfig;
import reactor.core.publisher.Flux;

import java.util.function.Consumer;

/**
 * LLM 客户端门面：基于 ai_model_config 配置直连上游 OpenAI 兼容端点
 */
public interface LlmClient {

    /**
     * 非流式对话
     */
    ChatResponse chat(ChatRequest request, AiModelConfig modelConfig);

    /**
     * 流式对话：元素为 SSE data 帧 JSON 字符串（{"content":"…"} 增量、[DONE] 结束、{"error":true,"message":"…"} 异常）
     */
    Flux<String> stream(ChatRequest request, AiModelConfig modelConfig);

    /**
     * 决策类调用：流式聚合为完整响应（独立收紧的 decision-timeout），文本增量经 onDelta 回调
     * （决策节点用于推送 THOUGHT 事件）；失败时返回 success=false 的响应（由调用方决定重试/降级）
     */
    ChatResponse chatForDecision(ChatRequest request, AiModelConfig modelConfig, Consumer<String> onDelta);
}
