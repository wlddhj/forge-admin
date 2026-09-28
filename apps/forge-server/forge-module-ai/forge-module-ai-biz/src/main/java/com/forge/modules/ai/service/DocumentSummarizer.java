package com.forge.modules.ai.service;

import com.forge.modules.ai.client.LlmClient;
import com.forge.modules.ai.dto.request.ChatRequest;
import com.forge.modules.ai.dto.response.ChatResponse;
import com.forge.modules.ai.dto.response.DocumentResponse;
import com.forge.modules.ai.entity.AiModelConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 文档摘要生成：移植原 Python summarizer 的风格 prompt 模板
 */
@Component
@RequiredArgsConstructor
public class DocumentSummarizer {

    private static final Map<String, String> STYLE_PROMPTS = Map.of(
            "brief", "请用简洁的语言总结以下内容的核心要点，不超过{max_length}字。",
            "detailed", "请详细总结以下内容的主要观点和关键信息，包含具体细节，不超过{max_length}字。",
            "bullet", "请用要点列表的形式总结以下内容，每个要点一行，总字数不超过{max_length}字。"
    );

    private static final double TEMPERATURE = 0.5;

    private final LlmClient llmClient;

    /**
     * 生成摘要；失败时返回 status=2 + errorMessage
     */
    public DocumentResponse summarize(String text, String style, Integer maxLength, AiModelConfig modelConfig) {
        DocumentResponse response = new DocumentResponse();
        String prompt = STYLE_PROMPTS.getOrDefault(style == null ? "brief" : style, STYLE_PROMPTS.get("brief"))
                .replace("{max_length}", String.valueOf(maxLength == null ? 500 : maxLength));

        ChatRequest request = new ChatRequest();
        ChatRequest.MessageItem system = new ChatRequest.MessageItem();
        system.setRole("system");
        system.setContent(prompt);
        request.setMessages(List.of(system));
        request.setContent("以下是需要总结的内容:\n\n" + text);
        request.setTemperature(TEMPERATURE);
        request.setMaxTokens((maxLength == null ? 500 : maxLength) * 2);

        ChatResponse chatResponse = llmClient.chat(request, modelConfig);
        if (chatResponse != null && Boolean.TRUE.equals(chatResponse.getSuccess())) {
            response.setStatus(1);
            response.setSummary(chatResponse.getContent());
            response.setModelName(modelConfig.getModelName());
        } else {
            response.setStatus(2);
            response.setErrorMessage(chatResponse != null ? chatResponse.getErrorMessage() : "摘要生成失败");
        }
        return response;
    }
}
