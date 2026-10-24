package com.forge.modules.ai.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.forge.modules.ai.config.AiModuleConfig;
import com.forge.modules.ai.dto.request.ChatRequest;
import com.forge.modules.ai.dto.response.ChatResponse;
import com.forge.modules.ai.entity.AiModelConfig;
import com.forge.modules.ai.mapper.AiModelConfigMapper;
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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 决策类调用单测：流式聚合、增量回调、超时失败、备用模型并行竞速首成功即返回（AC2）
 */
@ExtendWith(MockitoExtension.class)
class SpringAiLlmClientDecisionTest {

    @Mock
    private OpenAiChatModelFactory modelFactory;
    @Mock
    private AiModelConfigMapper modelConfigMapper;
    @Mock
    private SysConfigService sysConfigService;

    private AiModuleConfig aiModuleConfig;
    private SpringAiLlmClient client;
    private ExecutorService executor;

    private final AiModelConfig primary = model(1L, "主模型");
    private final AiModelConfig backup = model(2L, "备用模型A");

    @BeforeEach
    void setUp() {
        aiModuleConfig = new AiModuleConfig();
        aiModuleConfig.getClient().setDecisionTimeout(2000);
        executor = Executors.newFixedThreadPool(4);
        client = new SpringAiLlmClient(modelFactory, new ObjectMapper(), modelConfigMapper,
                sysConfigService, aiModuleConfig, executor,
                new ModelHealthCache(new com.forge.framework.localcache.LocalCaches(), aiModuleConfig));
        lenient().when(sysConfigService.getConfigValueByKey("ai.model.fallback-enabled")).thenReturn("1");
    }

    private AiModelConfig model(Long id, String name) {
        AiModelConfig config = new AiModelConfig();
        config.setId(id);
        config.setModelName(name);
        config.setModelCode(name);
        config.setModelType("text");
        config.setStatus(1);
        config.setMaxTokens(4096);
        return config;
    }

    private ChatRequest request() {
        ChatRequest request = new ChatRequest();
        request.setContent("决策");
        return request;
    }

    private org.springframework.ai.chat.model.ChatResponse chunk(String text) {
        return new org.springframework.ai.chat.model.ChatResponse(
                List.of(new Generation(new AssistantMessage(text))));
    }

    @Test
    void 流式聚合并回调增量() {
        OpenAiChatModel model = mock(OpenAiChatModel.class);
        when(modelFactory.get(primary)).thenReturn(model);
        when(model.stream(any(Prompt.class))).thenReturn(Flux.just(chunk("{\"act"), chunk("ion\":\"final\"}")));

        List<String> deltas = new ArrayList<>();
        ChatResponse response = client.chatForDecision(request(), primary, deltas::add);

        assertThat(response.getSuccess()).isTrue();
        assertThat(response.getContent()).isEqualTo("{\"action\":\"final\"}");
        assertThat(deltas).containsExactly("{\"act", "ion\":\"final\"}");
    }

    @Test
    void 超时返回失败并带超时提示() {
        OpenAiChatModel model = mock(OpenAiChatModel.class);
        when(modelFactory.get(primary)).thenReturn(model);
        // 永不发出元素的流触发 decision-timeout（2s）
        when(model.stream(any(Prompt.class)))
                .thenReturn(Flux.never().map(v -> (org.springframework.ai.chat.model.ChatResponse) null));
        when(modelConfigMapper.selectList(any())).thenReturn(List.of());

        ChatResponse response = client.chatForDecision(request(), primary, null);

        assertThat(response.getSuccess()).isFalse();
        assertThat(response.getErrorMessage()).contains("超时");
    }

    @Test
    void 主失败_备用并行竞速成功() {
        OpenAiChatModel failModel = mock(OpenAiChatModel.class);
        OpenAiChatModel okModel = mock(OpenAiChatModel.class);
        when(modelFactory.get(primary)).thenReturn(failModel);
        when(modelFactory.get(backup)).thenReturn(okModel);
        when(failModel.stream(any(Prompt.class)))
                .thenThrow(new RuntimeException("connect refused"));
        when(okModel.stream(any(Prompt.class))).thenReturn(Flux.just(chunk("{\"action\":\"final\"}")));
        when(modelConfigMapper.selectList(any())).thenReturn(List.of(backup));

        ChatResponse response = client.chatForDecision(request(), primary, null);

        assertThat(response.getSuccess()).isTrue();
        assertThat(response.getModelName()).isEqualTo("备用模型A");
    }

    @Test
    void 决策流错误信息透传为失败响应() {
        OpenAiChatModel model = mock(OpenAiChatModel.class);
        when(modelFactory.get(primary)).thenReturn(model);
        when(model.stream(any(Prompt.class)))
                .thenReturn(Flux.error(new RuntimeException("Connection refused")));
        when(modelConfigMapper.selectList(any())).thenReturn(List.of());

        ChatResponse response = client.chatForDecision(request(), primary, null);

        assertThat(response.getSuccess()).isFalse();
        assertThat(response.getErrorMessage()).isNotBlank();
    }
}
