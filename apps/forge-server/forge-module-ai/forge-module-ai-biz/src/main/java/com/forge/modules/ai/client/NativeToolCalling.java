package com.forge.modules.ai.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.forge.framework.localcache.LocalCaches;
import com.forge.modules.ai.agent.tools.AgentTool;
import com.forge.modules.ai.agent.tools.ToolRegistry;
import com.forge.modules.ai.config.AiModuleConfig;
import com.forge.modules.ai.dto.request.ChatRequest;
import com.forge.modules.ai.dto.response.ChatResponse;
import com.forge.modules.ai.entity.AiModelConfig;
import com.forge.modules.system.service.SysConfigService;
import com.google.common.cache.Cache;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.MessageAggregator;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * 原生 tool-calling 支持：决策调用优先走 OpenAI tools API（结构化工具选择，无 JSON 协议解析损耗），
 * 文本增量经 onDelta 透传（THOUGHT 事件）；auto 模式下检测到端点不支持 tools 时按模型指纹标记并回退
 * JSON 协议（后续调用直达 json 路径）。模式开关：sys_config ai.agent.tool-calling-mode = auto|native|json。
 */
@Slf4j
@Component
public class NativeToolCalling {

    public static final String MODE_CONFIG_KEY = "ai.agent.tool-calling-mode";

    private final OpenAiChatModelFactory modelFactory;
    private final ObjectMapper objectMapper;
    private final SysConfigService sysConfigService;
    private final AiModuleConfig aiModuleConfig;
    /** 端点不支持 tools 的模型指纹缓存（配置变更指纹变化后自动重新探测） */
    private final Cache<String, Boolean> unsupportedCache;

    public NativeToolCalling(OpenAiChatModelFactory modelFactory,
                             ObjectMapper objectMapper,
                             SysConfigService sysConfigService,
                             AiModuleConfig aiModuleConfig,
                             LocalCaches localCaches) {
        this.modelFactory = modelFactory;
        this.objectMapper = objectMapper;
        this.sysConfigService = sysConfigService;
        this.aiModuleConfig = aiModuleConfig;
        this.unsupportedCache = localCaches.get("ai-native-tools-unsupported",
                b -> b.maximumSize(500));
    }

    /**
     * 当前模型是否走原生 tool-calling（模式开关 + 模型级不支持标记）
     */
    public boolean enabled(AiModelConfig model) {
        String mode = mode();
        if ("json".equals(mode)) {
            return false;
        }
        if (unsupportedCache.getIfPresent(fingerprint(model)) != null) {
            return false;
        }
        return true;
    }

    /**
     * 原生决策调用：tools 注入 + internalToolExecutionEnabled(false)（由图路由接管执行与高危门控），
     * 流式聚合 + decision-timeout；toolCalls 转换为决策 JSON 文本（与 DecisionJsonParser 协议一致），
     * 纯文本回答包装为 final 决策。端点不支持时抛 ToolsUnsupportedException 供调用方回退。
     */
    public ChatResponse call(ChatRequest request, AiModelConfig model,
                             ToolRegistry toolRegistry, Collection<String> allowedTools,
                             Consumer<String> onDelta) {
        long start = System.currentTimeMillis();
        ChatResponse response = new ChatResponse();
        response.setModelName(model.getModelName());
        try {
            ChatModel chatModel = modelFactory.get(model);
            List<org.springframework.ai.tool.ToolCallback> callbacks = new ArrayList<>();
            for (AgentTool tool : toolRegistry.all()) {
                if (allowedTools == null || allowedTools.isEmpty() || allowedTools.contains(tool.name())) {
                    callbacks.add(new AgentToolCallback(tool, objectMapper));
                }
            }
            OpenAiChatOptions.Builder optionsBuilder = OpenAiChatOptions.builder()
                    .model(model.getModelCode())
                    .toolCallbacks(callbacks)
                    .internalToolExecutionEnabled(false)
                    .parallelToolCalls(true);
            if (request.getTemperature() != null) {
                optionsBuilder.temperature(request.getTemperature());
            }
            Prompt prompt = new Prompt(List.of(new UserMessage(request.getContent())), optionsBuilder.build());

            long timeout = aiModuleConfig.getClient().getDecisionTimeout() == null
                    ? 45000L : aiModuleConfig.getClient().getDecisionTimeout();
            StringBuilder content = new StringBuilder();
            AtomicReference<org.springframework.ai.chat.model.ChatResponse> aggregated = new AtomicReference<>();
            MessageAggregator aggregator = new MessageAggregator();
            org.springframework.ai.chat.model.ChatResponse aiResponse = aggregator.aggregate(
                            chatModel.stream(prompt).doOnNext(chunk -> {
                                String delta = chunk.getResult() == null || chunk.getResult().getOutput() == null
                                        ? null : chunk.getResult().getOutput().getText();
                                if (delta != null && !delta.isEmpty()) {
                                    content.append(delta);
                                    if (onDelta != null) {
                                        onDelta.accept(delta);
                                    }
                                }
                            }), aggregated::set)
                    .blockLast(java.time.Duration.ofMillis(timeout));

            org.springframework.ai.chat.model.ChatResponse source =
                    aggregated.get() != null ? aggregated.get() : aiResponse;
            AssistantMessage output = source != null && source.getResult() != null
                    ? source.getResult().getOutput() : null;
            response.setSuccess(true);
            response.setResponseTime(System.currentTimeMillis() - start);
            if (output != null && output.hasToolCalls()) {
                Map<String, Object> decision = new LinkedHashMap<>();
                decision.put("action", "tool");
                List<Map<String, Object>> calls = new ArrayList<>();
                for (AssistantMessage.ToolCall call : output.getToolCalls()) {
                    Map<String, Object> c = new LinkedHashMap<>();
                    c.put("tool", call.name());
                    c.put("args", parseArgs(call.arguments()));
                    calls.add(c);
                }
                decision.put("calls", calls);
                response.setContent(objectMapper.writeValueAsString(decision));
            } else {
                String text = output == null || output.getText() == null ? content.toString() : output.getText();
                Map<String, Object> decision = new LinkedHashMap<>();
                decision.put("action", "final");
                decision.put("answer", text == null ? "" : text);
                response.setContent(objectMapper.writeValueAsString(decision));
            }
            if (source != null && source.getMetadata() != null && source.getMetadata().getUsage() != null) {
                response.setInputTokens(source.getMetadata().getUsage().getPromptTokens());
                response.setOutputTokens(source.getMetadata().getUsage().getCompletionTokens());
            }
        } catch (Exception e) {
            if (isToolsUnsupported(e)) {
                log.warn("模型 [{}] 端点不支持原生 tools，标记并回退 JSON 协议: {}", model.getModelName(), e.getMessage());
                unsupportedCache.put(fingerprint(model), Boolean.TRUE);
                throw new ToolsUnsupportedException("端点不支持原生 tool-calling: " + e.getMessage(), e);
            }
            throw e instanceof RuntimeException runtimeException ? runtimeException : new RuntimeException(e);
        }
        return response;
    }

    /** 不支持特征：4xx 客户端错误且报文提及 tool/function */
    private boolean isToolsUnsupported(Throwable e) {
        String message = String.valueOf(e.getMessage()).toLowerCase();
        boolean clientError = message.contains("400") || message.contains("404")
                || message.contains("422") || message.contains("bad request")
                || message.contains("invalid_request");
        boolean mentionsTool = message.contains("tool") || message.contains("function");
        return clientError && mentionsTool;
    }

    private String mode() {
        try {
            String value = sysConfigService.getConfigValueByKey(MODE_CONFIG_KEY);
            return value == null || value.isBlank() ? "auto" : value.trim().toLowerCase();
        } catch (Exception e) {
            return "auto";
        }
    }

    private Map<String, Object> parseArgs(String arguments) {
        if (arguments == null || arguments.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(arguments,
                    objectMapper.getTypeFactory().constructMapType(Map.class, String.class, Object.class));
        } catch (Exception e) {
            return Map.of("raw", arguments);
        }
    }

    private String fingerprint(AiModelConfig model) {
        return Objects.toString(model.getId(), "") + "|" + Objects.toString(model.getUpdateTime(), "");
    }

    /** 端点不支持原生 tools：调用方捕获后回退 JSON 协议路径 */
    public static class ToolsUnsupportedException extends RuntimeException {
        public ToolsUnsupportedException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
