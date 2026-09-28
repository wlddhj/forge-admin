package com.forge.modules.ai.client;

import com.forge.modules.ai.dto.request.ChatRequest;
import com.forge.modules.ai.dto.response.ChatResponse;
import com.forge.modules.ai.entity.AiModelConfig;
import reactor.core.publisher.Flux;

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
}
