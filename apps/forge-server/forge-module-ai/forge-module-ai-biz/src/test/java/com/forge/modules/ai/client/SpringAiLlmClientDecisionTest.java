package com.forge.modules.ai.client;

import tools.jackson.databind.ObjectMapper;
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
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * 决策类调用单测：流式聚合、增量回调（含 reasoning）、超时失败、备用模型并行竞速首成功即返回（AC2）
 *
 * 决策通路已切原生 SSE 客户端（reasoning 透传），此处 mock OpenAiCompatStreamClient。
 */
@ExtendWith(MockitoExtension.class)
class SpringAiLlmClientDecisionTest {

    @Mock
    private AiModelConfigMapper modelConfigMapper;
    @Mock
    private SysConfigService sysConfigService;
    @Mock
    private OpenAiCompatStreamClient streamClient;

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
        client = new SpringAiLlmClient(modelFactory(), new ObjectMapper(), modelConfigMapper,
                sysConfigService, aiModuleConfig, executor, executor,
                new ModelHealthCache(new com.forge.framework.localcache.LocalCaches(), aiModuleConfig),
                com.forge.modules.ai.config.TestAiMetrics.noop(), streamClient);
        lenient().when(sysConfigService.getConfigValueByKey("ai.model.fallback-enabled")).thenReturn("1");
    }

    private OpenAiChatModelFactory modelFactory() {
        return org.mockito.Mockito.mock(OpenAiChatModelFactory.class);
    }

    private AiModelConfig model(Long id, String name) {
        AiModelConfig config = new AiModelConfig();
        config.setId(id);
        config.setModelName(name);
        config.setModelCode(name);
        config.setModelType("text");
        config.setStatus(1);
        config.setMaxTokens(4096);
        config.setApiEndpoint("http://llm.fake/v4");
        config.setApiKey("sk-test");
        return config;
    }

    private ChatRequest request() {
        ChatRequest request = new ChatRequest();
        request.setContent("决策");
        return request;
    }

    @Test
    void 流式聚合并回调增量() {
        when(streamClient.stream(any(OpenAiCompatStreamClient.StreamRequest.class))).thenReturn(Flux.just(
                StreamFrame.content("{\"act"), StreamFrame.content("ion\":\"final\"}"),
                StreamFrame.done(10L, 5L)));

        List<String> deltas = new ArrayList<>();
        ChatResponse response = client.chatForDecision(request(), primary, deltas::add, null);

        assertThat(response.getSuccess()).isTrue();
        assertThat(response.getContent()).isEqualTo("{\"action\":\"final\"}");
        assertThat(deltas).containsExactly("{\"act", "ion\":\"final\"}");
        assertThat(response.getInputTokens()).isEqualTo(10);
        assertThat(response.getOutputTokens()).isEqualTo(5);
    }

    @Test
    void 思考增量经onReasoning回调且不计入正文() {
        when(streamClient.stream(any(OpenAiCompatStreamClient.StreamRequest.class))).thenReturn(Flux.just(
                StreamFrame.reasoning("先分析"),
                StreamFrame.reasoning("再决策"),
                StreamFrame.content("{\"action\":\"final\"}"),
                StreamFrame.done(null, null)));

        List<String> deltas = new ArrayList<>();
        List<String> reasonings = new ArrayList<>();
        ChatResponse response = client.chatForDecision(request(), primary, deltas::add, reasonings::add);

        assertThat(response.getSuccess()).isTrue();
        assertThat(reasonings).containsExactly("先分析", "再决策");
        assertThat(deltas).containsExactly("{\"action\":\"final\"}");
        assertThat(response.getContent()).isEqualTo("{\"action\":\"final\"}");
    }

    @Test
    void 超时返回失败并带超时提示() {
        // 永不发出元素的流触发 decision-timeout（2s）
        when(streamClient.stream(any(OpenAiCompatStreamClient.StreamRequest.class)))
                .thenReturn(Flux.never());
        when(modelConfigMapper.selectList(any())).thenReturn(List.of());

        ChatResponse response = client.chatForDecision(request(), primary, null, null);

        assertThat(response.getSuccess()).isFalse();
        assertThat(response.getErrorMessage()).contains("超时");
    }

    @Test
    void 主失败_备用并行竞速成功() {
        when(streamClient.stream(any(OpenAiCompatStreamClient.StreamRequest.class)))
                .thenReturn(Flux.error(new OpenAiCompatStreamClient.UpstreamStreamException(502, "bad gateway")),
                        Flux.just(StreamFrame.content("{\"action\":\"final\"}"), StreamFrame.done(null, null)));
        when(modelConfigMapper.selectList(any())).thenReturn(List.of(backup));

        ChatResponse response = client.chatForDecision(request(), primary, null, null);

        assertThat(response.getSuccess()).isTrue();
        assertThat(response.getModelName()).isEqualTo("备用模型A");
    }

    @Test
    void 决策流错误信息透传为失败响应() {
        when(streamClient.stream(any(OpenAiCompatStreamClient.StreamRequest.class)))
                .thenReturn(Flux.error(new OpenAiCompatStreamClient.UpstreamStreamException("上游模型连接失败: refused", null)));
        when(modelConfigMapper.selectList(any())).thenReturn(List.of());

        ChatResponse response = client.chatForDecision(request(), primary, null, null);

        assertThat(response.getSuccess()).isFalse();
        assertThat(response.getErrorMessage()).isNotBlank();
    }
}
