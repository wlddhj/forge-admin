package com.forge.modules.ai.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.forge.modules.ai.dto.request.ChatRequest;
import com.forge.modules.ai.dto.response.ChatResponse;
import com.forge.modules.ai.entity.AiModelConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * LLM 客户端实现：Spring AI 直连上游 OpenAI 兼容端点。
 * 流式帧契约与原 Python 服务一致：{"content":"…"} 增量 → [DONE]；异常 {"error":true,"message":"…"}
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SpringAiLlmClient implements LlmClient {

    public static final String DONE_FRAME = "[DONE]";

    private final OpenAiChatModelFactory modelFactory;
    private final ObjectMapper objectMapper;

    @Override
    public ChatResponse chat(ChatRequest request, AiModelConfig modelConfig) {
        ChatResponse response = new ChatResponse();
        long start = System.currentTimeMillis();
        try {
            ChatModel chatModel = modelFactory.get(modelConfig);
            org.springframework.ai.chat.model.ChatResponse aiResponse = chatModel.call(buildPrompt(request, modelConfig));

            response.setSuccess(true);
            response.setModelName(modelConfig.getModelName());
            response.setResponseTime(System.currentTimeMillis() - start);
            if (aiResponse != null) {
                if (aiResponse.getResult() != null && aiResponse.getResult().getOutput() != null) {
                    response.setContent(aiResponse.getResult().getOutput().getText());
                }
                Usage usage = aiResponse.getMetadata() != null ? aiResponse.getMetadata().getUsage() : null;
                if (usage != null) {
                    response.setInputTokens(usage.getPromptTokens());
                    response.setOutputTokens(usage.getCompletionTokens());
                }
            }
        } catch (Exception e) {
            String friendly = friendlyError(e);
            log.error("LLM 非流式调用失败 [{}]: {}", modelConfig.getModelName(), friendly, e);
            response.setSuccess(false);
            response.setErrorMessage(friendly);
            response.setResponseTime(System.currentTimeMillis() - start);
        }
        return response;
    }

    @Override
    public Flux<String> stream(ChatRequest request, AiModelConfig modelConfig) {
        ChatModel chatModel = modelFactory.get(modelConfig);
        Prompt prompt = buildPrompt(request, modelConfig);

        return chatModel.stream(prompt)
                .mapNotNull(aiResponse -> {
                    if (aiResponse.getResult() == null || aiResponse.getResult().getOutput() == null) {
                        return null;
                    }
                    String text = aiResponse.getResult().getOutput().getText();
                    return text == null || text.isEmpty() ? null : contentFrame(text);
                })
                .concatWith(Flux.just(DONE_FRAME))
                .onErrorResume(e -> {
                    String friendly = friendlyError(e instanceof Exception ex ? ex : new RuntimeException(e));
                    log.error("LLM 流式调用失败 [{}]: {}", modelConfig.getModelName(), friendly, e);
                    return Flux.just(errorFrame(friendly));
                });
    }

    // ========== Prompt 构建 ==========

    private Prompt buildPrompt(ChatRequest request, AiModelConfig modelConfig) {
        List<Message> messages = new ArrayList<>();
        if (request.getMessages() != null) {
            for (ChatRequest.MessageItem item : request.getMessages()) {
                if (item.getContent() == null || item.getContent().isEmpty()) {
                    continue;
                }
                if ("assistant".equals(item.getRole())) {
                    messages.add(new AssistantMessage(item.getContent()));
                } else if ("system".equals(item.getRole())) {
                    messages.add(new SystemMessage(item.getContent()));
                } else {
                    messages.add(new UserMessage(item.getContent()));
                }
            }
        }
        messages.add(new UserMessage(request.getContent()));

        OpenAiChatOptions.Builder options = OpenAiChatOptions.builder().model(modelConfig.getModelCode());
        if (request.getTemperature() != null) {
            options.temperature(request.getTemperature());
        }
        if (request.getMaxTokens() != null) {
            options.maxTokens(request.getMaxTokens());
        }
        return new Prompt(messages, options.build());
    }

    // ========== SSE 帧（Jackson 序列化保证换行等字符正确转义，等价 Python json.dumps） ==========

    private String contentFrame(String text) {
        return toJson(Map.of("content", text));
    }

    private String errorFrame(String message) {
        Map<String, Object> frame = new LinkedHashMap<>();
        frame.put("error", true);
        frame.put("message", message);
        return toJson(frame);
    }

    private String toJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            return "{\"error\":true,\"message\":\"响应序列化失败\"}";
        }
    }

    // ========== 错误文案（沿用原 PythonServiceErrorHandler 语义） ==========

    private String friendlyError(Exception e) {
        if (e instanceof WebClientResponseException responseException) {
            return switch (responseException.getStatusCode().value()) {
                case 401, 403 -> "模型 API 密钥无效或无权限，请检查模型配置";
                case 429 -> "上游模型服务请求过于频繁，请稍后重试";
                case 504 -> "上游模型服务响应超时，请稍后重试";
                case 500, 502, 503 -> "上游模型服务内部错误，请稍后重试";
                default -> "请求上游模型失败: " + responseException.getStatusCode().value();
            };
        }
        String message = e.getMessage() == null ? "" : e.getMessage();
        if (message.contains("Connection refused") || message.contains("ConnectException")) {
            return "无法连接模型服务端点，请检查模型配置的 API 端点";
        }
        if (message.contains("TimeoutException") || message.contains("timeout")
                || message.contains("ReadTimeout") || message.contains("responseTimeout")) {
            return "模型服务响应超时，请稍后重试";
        }
        return "调用模型服务失败: " + shorten(message);
    }

    private String shorten(String message) {
        return message.length() > 200 ? message.substring(0, 200) + "…" : message;
    }
}
