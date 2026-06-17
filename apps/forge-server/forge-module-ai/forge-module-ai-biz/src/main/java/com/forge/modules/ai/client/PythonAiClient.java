package com.forge.modules.ai.client;

import com.forge.modules.ai.dto.request.ChatRequest;
import com.forge.modules.ai.dto.request.DocumentSummaryRequest;
import com.forge.modules.ai.dto.response.ChatResponse;
import com.forge.modules.ai.dto.response.DocumentResponse;
import com.forge.modules.ai.dto.response.ModelListResponse;
import reactor.core.publisher.Flux;

import java.util.Map;

/**
 * Python AI服务客户端接口
 */
public interface PythonAiClient {

    /**
     * 发送聊天请求
     */
    ChatResponse chat(ChatRequest request);

    /**
     * 流式聊天（返回SSE数据流）
     */
    Flux<String> chatStreamFlux(ChatRequest request);

    /**
     * 生成文档摘要
     */
    DocumentResponse summarize(DocumentSummaryRequest request);

    /**
     * 解析文档
     */
    DocumentResponse parseDocument(Long documentId, String filePath);

    /**
     * 获取可用模型列表
     */
    ModelListResponse getAvailableModels();

    /**
     * 健康检查
     */
    Map<String, Object> healthCheck();

    /**
     * 检查模型是否可用
     */
    boolean checkModelAvailable(String modelName);
}