package com.forge.modules.ai.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.forge.modules.ai.dto.request.ChatRequest;
import com.forge.modules.ai.dto.request.DocumentSummaryRequest;
import com.forge.modules.ai.dto.response.ChatResponse;
import com.forge.modules.ai.dto.response.DocumentResponse;
import com.forge.modules.ai.dto.response.ModelListResponse;
import com.forge.modules.ai.exception.PythonServiceErrorHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Python AI服务客户端实现
 */
@Slf4j
@Component
public class PythonAiClientImpl implements PythonAiClient {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public PythonAiClientImpl(
            @Qualifier("pythonServiceWebClient") WebClient webClient,
            ObjectMapper objectMapper) {
        this.webClient = webClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public ChatResponse chat(ChatRequest request) {
        try {
            Map<String, Object> pythonRequest = convertToPythonRequest(request);

            return webClient.post()
                    .uri("/api/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(pythonRequest)
                    .retrieve()
                    .bodyToMono(ChatResponse.class)
                    .block();
        } catch (Exception e) {
            String friendlyMessage = PythonServiceErrorHandler.handleException(e, "chat");
            log.error("调用Python AI服务chat接口失败: {}", friendlyMessage, e);
            ChatResponse errorResponse = new ChatResponse();
            errorResponse.setSuccess(false);
            errorResponse.setErrorMessage(friendlyMessage);
            return errorResponse;
        }
    }

    @Override
    public Flux<String> chatStreamFlux(ChatRequest request) {
        request.setStream(true);
        Map<String, Object> pythonRequest = convertToPythonRequest(request);

        return webClient.post()
                .uri("/api/chat/completions/stream")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(pythonRequest)
                .retrieve()
                .bodyToFlux(String.class)
                .onErrorResume(e -> {
                    String friendlyMessage = PythonServiceErrorHandler.handleException(e, "chat-stream");
                    log.error("调用Python AI服务流式chat接口失败: {}", friendlyMessage, e);
                    // 返回包含错误信息的 SSE 格式消息
                    String errorJson = String.format(
                            "{\"error\": true, \"message\": \"%s\"}",
                            friendlyMessage.replace("\"", "\\\"")
                    );
                    return Flux.just(errorJson);
                });
    }

    private Map<String, Object> convertToPythonRequest(ChatRequest request) {
        Map<String, Object> pythonRequest = new HashMap<>();

        List<Map<String, String>> messages = new ArrayList<>();
        // 使用历史消息（如果有）
        if (request.getMessages() != null && !request.getMessages().isEmpty()) {
            for (ChatRequest.MessageItem m : request.getMessages()) {
                Map<String, String> msg = new HashMap<>();
                msg.put("role", m.getRole());
                msg.put("content", m.getContent());
                messages.add(msg);
            }
        }
        // 添加当前用户消息
        Map<String, String> userMessage = new HashMap<>();
        userMessage.put("role", "user");
        userMessage.put("content", request.getContent());
        messages.add(userMessage);
        pythonRequest.put("messages", messages);

        String provider = inferProviderFromModelName(request.getModelName());
        pythonRequest.put("provider", provider);
        pythonRequest.put("model", request.getModelName());

        if (request.getTemperature() != null) {
            pythonRequest.put("temperature", request.getTemperature());
        }
        if (request.getMaxTokens() != null) {
            pythonRequest.put("max_tokens", request.getMaxTokens());
        }
        pythonRequest.put("stream", request.getStream() != null && request.getStream());

        return pythonRequest;
    }

    @Override
    public DocumentResponse summarize(DocumentSummaryRequest request) {
        try {
            // 构建 Python 期望的请求格式
            Map<String, Object> pythonRequest = new HashMap<>();
            pythonRequest.put("text", request.getText());

            // 优先使用 provider 字段，否则从 modelName 推断
            String provider = request.getProvider();
            if (provider == null || provider.isEmpty()) {
                provider = inferProviderFromModelName(request.getModelName());
            }
            pythonRequest.put("provider", provider);

            // 映射字段名称
            pythonRequest.put("style", request.getStyle());
            pythonRequest.put("max_length", request.getMaxLength());

            return webClient.post()
                    .uri("/api/document/summarize")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(pythonRequest)
                    .retrieve()
                    .bodyToMono(DocumentResponse.class)
                    .block();
        } catch (Exception e) {
            String friendlyMessage = PythonServiceErrorHandler.handleException(e, "summarize");
            log.error("调用Python AI服务summarize接口失败: {}", friendlyMessage, e);
            DocumentResponse errorResponse = new DocumentResponse();
            errorResponse.setStatus(2);
            errorResponse.setErrorMessage(friendlyMessage);
            return errorResponse;
        }
    }

    @Override
    public DocumentResponse parseDocument(Long documentId, String filePath) {
        try {
            Map<String, Object> params = new HashMap<>();
            params.put("documentId", documentId);
            params.put("filePath", filePath);

            return webClient.post()
                    .uri("/api/document/parse")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(params)
                    .retrieve()
                    .bodyToMono(DocumentResponse.class)
                    .block();
        } catch (Exception e) {
            String friendlyMessage = PythonServiceErrorHandler.handleException(e, "parse-document");
            log.error("调用Python AI服务parse接口失败: {}", friendlyMessage, e);
            DocumentResponse errorResponse = new DocumentResponse();
            errorResponse.setStatus(2);
            errorResponse.setErrorMessage(friendlyMessage);
            return errorResponse;
        }
    }

    @Override
    public DocumentResponse parseDocumentFile(Long documentId, MultipartFile file) {
        try {
            // 使用 MultipartBodyBuilder 构建 multipart/form-data 请求
            MultipartBodyBuilder bodyBuilder = new MultipartBodyBuilder();

            if (documentId != null) {
                bodyBuilder.part("documentId", documentId.toString());
            }

            bodyBuilder.part("file", file.getResource())
                    .filename(file.getOriginalFilename() != null ? file.getOriginalFilename() : "unknown")
                    .contentType(MediaType.parseMediaType(file.getContentType() != null ? file.getContentType() : "application/octet-stream"));

            return webClient.post()
                    .uri("/api/document/parse")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .bodyValue(bodyBuilder.build())
                    .retrieve()
                    .bodyToMono(DocumentResponse.class)
                    .block();
        } catch (Exception e) {
            String friendlyMessage = PythonServiceErrorHandler.handleException(e, "parse-document-file");
            log.error("调用Python AI服务parse接口（文件上传）失败: {}", friendlyMessage, e);
            DocumentResponse errorResponse = new DocumentResponse();
            errorResponse.setStatus(2);
            errorResponse.setErrorMessage(friendlyMessage);
            return errorResponse;
        }
    }

    @Override
    public ModelListResponse getAvailableModels() {
        try {
            return webClient.get()
                    .uri("/api/chat/providers")
                    .retrieve()
                    .bodyToMono(ModelListResponse.class)
                    .block();
        } catch (Exception e) {
            String friendlyMessage = PythonServiceErrorHandler.handleException(e, "get-models");
            log.error("调用Python AI服务models接口失败: {}", friendlyMessage, e);
            ModelListResponse errorResponse = new ModelListResponse();
            errorResponse.setAvailable(false);
            errorResponse.setErrorMessage(friendlyMessage);
            return errorResponse;
        }
    }

    @Override
    public Map<String, Object> healthCheck() {
        try {
            String response = webClient.get()
                    .uri("/api/health")
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            return objectMapper.readValue(response, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return PythonServiceErrorHandler.getUnavailableStatus(e);
        }
    }

    @Override
    public boolean checkModelAvailable(String modelName) {
        try {
            Map<String, Object> health = healthCheck();
            if (health.containsKey("providers")) {
                Object providersObj = health.get("providers");
                List<String> availableProviders = new ArrayList<>();
                if (providersObj instanceof List) {
                    for (Object p : (List<?>) providersObj) {
                        availableProviders.add(p.toString());
                    }
                }
                String provider = inferProviderFromModelName(modelName);
                return availableProviders.contains(provider);
            }
            return false;
        } catch (Exception e) {
            log.warn("检查模型 {} 可用性失败: {}", modelName, e.getMessage());
            return false;
        }
    }

    private String inferProviderFromModelName(String modelName) {
        if (modelName == null) {
            return null;
        }
        String lowerName = modelName.toLowerCase();
        if (lowerName.startsWith("qwen") || lowerName.contains("通义")) {
            return "qwen";
        }
        if (lowerName.startsWith("deepseek")) {
            return "deepseek";
        }
        if (lowerName.startsWith("glm") || lowerName.contains("智谱")) {
            return "glm";
        }
        if (lowerName.startsWith("ernie") || lowerName.contains("文心")) {
            return "ernie";
        }
        return modelName.split("-")[0].toLowerCase();
    }
}