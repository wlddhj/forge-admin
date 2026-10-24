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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 原生 tool-calling 单测：模式开关、toolCalls→决策 JSON 转换、纯文本 final 包装、
 * 端点不支持检测与标记回退（AC7）
 */
@ExtendWith(MockitoExtension.class)
class NativeToolCallingTest {

    @Mock
    private OpenAiChatModelFactory modelFactory;
    @Mock
    private SysConfigService sysConfigService;

    private AiModuleConfig aiModuleConfig;
    private NativeToolCalling nativeToolCalling;
    private ToolRegistry toolRegistry;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final AiModelConfig model = new AiModelConfig();

    @BeforeEach
    void setUp() {
        aiModuleConfig = new AiModuleConfig();
        aiModuleConfig.getClient().setDecisionTimeout(2000);
        LocalCaches caches = new LocalCaches();
        nativeToolCalling = new NativeToolCalling(modelFactory, objectMapper, sysConfigService,
                aiModuleConfig, caches);
        toolRegistry = new ToolRegistry(List.of(new EchoTool()));
        model.setId(1L);
        model.setModelName("glm");
        model.setModelCode("glm-4.6");
        model.setModelType("text");
        lenient().when(sysConfigService.getConfigValueByKey(NativeToolCalling.MODE_CONFIG_KEY))
                .thenReturn("auto");
    }

    /** 无工具调用纯文本 → final 决策 JSON */
    @Test
    void 纯文本回答包装为final决策() {
        OpenAiChatModel chatModel = mock(OpenAiChatModel.class);
        when(modelFactory.get(model)).thenReturn(chatModel);
        when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.just(aiResponse("直接答案", null)));

        ChatResponse response = nativeToolCalling.call(request(), model, toolRegistry, null, null);

        assertThat(response.getSuccess()).isTrue();
        assertThat(response.getContent()).contains("\"action\":\"final\"").contains("直接答案");
    }

    /** toolCalls → tool 决策 JSON（与 DecisionJsonParser 协议一致） */
    @Test
    void 工具调用转换为tool决策() {
        OpenAiChatModel chatModel = mock(OpenAiChatModel.class);
        when(modelFactory.get(model)).thenReturn(chatModel);
        List<AssistantMessage.ToolCall> calls = List.of(
                new AssistantMessage.ToolCall("call-1", "function", "echo_tool", "{\"text\":\"hi\"}"));
        when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.just(aiResponse(null, calls)));

        ChatResponse response = nativeToolCalling.call(request(), model, toolRegistry, null, null);

        assertThat(response.getSuccess()).isTrue();
        Map<String, Object> decision = parse(response.getContent());
        assertThat(decision.get("action")).isEqualTo("tool");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> callList = (List<Map<String, Object>>) decision.get("calls");
        assertThat(callList.get(0).get("tool")).isEqualTo("echo_tool");
        assertThat(callList.get(0).get("args")).isEqualTo(Map.of("text", "hi"));
    }

    /** json 模式直接禁用 */
    @Test
    void json模式禁用native() {
        when(sysConfigService.getConfigValueByKey(NativeToolCalling.MODE_CONFIG_KEY)).thenReturn("json");
        assertThat(nativeToolCalling.enabled(model)).isFalse();
    }

    /** auto 模式默认启用 */
    @Test
    void auto模式默认启用() {
        assertThat(nativeToolCalling.enabled(model)).isTrue();
    }

    /** 400 + tool 报文 → 标记不支持并抛异常，后续 enabled=false */
    @Test
    void 端点不支持检测与标记回退() {
        OpenAiChatModel chatModel = mock(OpenAiChatModel.class);
        when(modelFactory.get(model)).thenReturn(chatModel);
        when(chatModel.stream(any(Prompt.class)))
                .thenThrow(new RuntimeException("400 Bad Request: tool is not supported"));

        assertThrows(NativeToolCalling.ToolsUnsupportedException.class,
                () -> nativeToolCalling.call(request(), model, toolRegistry, null, null));
        assertThat(nativeToolCalling.enabled(model)).isFalse();
    }

    /** 非 tool 相关错误不标记 */
    @Test
    void 普通错误不标记不支持() {
        OpenAiChatModel chatModel = mock(OpenAiChatModel.class);
        when(modelFactory.get(model)).thenReturn(chatModel);
        when(chatModel.stream(any(Prompt.class)))
                .thenThrow(new RuntimeException("500 Internal Error"));

        assertThrows(RuntimeException.class,
                () -> nativeToolCalling.call(request(), model, toolRegistry, null, null));
        assertThat(nativeToolCalling.enabled(model)).isTrue();
    }

    // ========== 工具 ==========

    @SuppressWarnings("unchecked")
    private Map<String, Object> parse(String json) {
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private ChatRequest request() {
        ChatRequest request = new ChatRequest();
        request.setContent("决策");
        request.setTemperature(0.1);
        return request;
    }

    private org.springframework.ai.chat.model.ChatResponse aiResponse(
            String text, List<AssistantMessage.ToolCall> toolCalls) {
        AssistantMessage.Builder builder = AssistantMessage.builder()
                .content(text == null ? "" : text)
                .toolCalls(toolCalls == null ? List.of() : toolCalls);
        return new org.springframework.ai.chat.model.ChatResponse(List.of(new Generation(builder.build())));
    }

    /** 测试用工具 */
    static class EchoTool implements AgentTool {
        @Override
        public String name() {
            return "echo_tool";
        }

        @Override
        public String description() {
            return "回显文本";
        }

        @Override
        public Map<String, String> parameterSpec() {
            return Map.of("text", "要回显的文本");
        }

        @Override
        public Object execute(Map<String, Object> args) {
            return Map.of("echo", args.get("text"));
        }
    }
}
