package com.forge.modules.ai.client;

import tools.jackson.databind.ObjectMapper;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * LLM 客户端流式契约单测：content/reasoning 帧、[DONE]、转义、空块跳过、错误帧
 *
 * 聊天流默认走原生 OpenAI 兼容通路（reasoning 透传），mock OpenAiCompatStreamClient；
 * chat() 非流式仍走 Spring AI，保留 chatModel 桩。
 */
@ExtendWith(MockitoExtension.class)
class SpringAiLlmClientTest {

    @Mock
    private OpenAiChatModelFactory modelFactory;
    @Mock
    private OpenAiChatModel chatModel;
    @Mock
    private AiModelConfigMapper modelConfigMapper;
    @Mock
    private SysConfigService sysConfigService;
    @Mock
    private OpenAiCompatStreamClient streamClient;

    private SpringAiLlmClient llmClient;
    private AiModelConfig modelConfig;

    @BeforeEach
    void setUp() {
        llmClient = new SpringAiLlmClient(modelFactory, new ObjectMapper(), modelConfigMapper, sysConfigService,
                new com.forge.modules.ai.config.AiModuleConfig(), java.util.concurrent.Executors.newFixedThreadPool(2), java.util.concurrent.Executors.newFixedThreadPool(2),
                new ModelHealthCache(new com.forge.framework.localcache.LocalCaches(),
                        new com.forge.modules.ai.config.AiModuleConfig()),
                com.forge.modules.ai.config.TestAiMetrics.noop(), streamClient);
        modelConfig = new AiModelConfig();
        modelConfig.setId(1L);
        modelConfig.setModelName("DeepSeek Chat");
        modelConfig.setModelCode("deepseek-chat");
        modelConfig.setApiEndpoint("https://api.deepseek.com/v1");
        modelConfig.setApiKey("sk-test");
        lenient().when(modelFactory.get(modelConfig)).thenReturn(chatModel);
    }

    private org.springframework.ai.chat.model.ChatResponse aiResponse(String text) {
        return new org.springframework.ai.chat.model.ChatResponse(
                List.of(new Generation(new AssistantMessage(text))));
    }

    private ChatRequest chatRequest() {
        ChatRequest request = new ChatRequest();
        request.setContent("hi");
        return request;
    }

    @Test
    void stream_产出前端SSE契约帧序列_content帧加DONE() {
        when(streamClient.stream(any(OpenAiCompatStreamClient.StreamRequest.class))).thenReturn(Flux.just(
                StreamFrame.content("你好"), StreamFrame.content("世界"), StreamFrame.done(null, null)));

        List<String> frames = llmClient.stream(chatRequest(), modelConfig).collectList().block();

        assertThat(frames).containsExactly(
                "{\"content\":\"你好\"}",
                "{\"content\":\"世界\"}",
                "[DONE]");
    }

    @Test
    void stream_思考模型产出reasoning帧加content帧() {
        when(streamClient.stream(any(OpenAiCompatStreamClient.StreamRequest.class))).thenReturn(Flux.just(
                StreamFrame.reasoning("让我想想"),
                StreamFrame.content("你好"),
                StreamFrame.done(3L, 2L)));

        List<String> frames = llmClient.stream(chatRequest(), modelConfig).collectList().block();

        assertThat(frames).containsExactly(
                "{\"reasoning\":\"让我想想\"}",
                "{\"content\":\"你好\"}",
                "[DONE]");
    }

    @Test
    void stream_换行符正确JSON转义_不破坏SSE帧() {
        when(streamClient.stream(any(OpenAiCompatStreamClient.StreamRequest.class)))
                .thenReturn(Flux.just(StreamFrame.content("第一行\n第二行"), StreamFrame.done(null, null)));

        List<String> frames = llmClient.stream(chatRequest(), modelConfig).collectList().block();

        assertThat(frames.get(0)).isEqualTo("{\"content\":\"第一行\\n第二行\"}");
        assertThat(frames).doesNotContain("\n第一行");
    }

    @Test
    void stream_空文本增量被跳过() {
        when(streamClient.stream(any(OpenAiCompatStreamClient.StreamRequest.class))).thenReturn(Flux.just(
                StreamFrame.content(""), StreamFrame.content("有效"), StreamFrame.done(null, null)));

        List<String> frames = llmClient.stream(chatRequest(), modelConfig).collectList().block();

        assertThat(frames).containsExactly("{\"content\":\"有效\"}", "[DONE]");
    }

    @Test
    void stream_上游异常输出error帧_无DONE() {
        when(streamClient.stream(any(OpenAiCompatStreamClient.StreamRequest.class)))
                .thenReturn(Flux.error(new OpenAiCompatStreamClient.UpstreamStreamException(503, "unavailable")));

        List<String> frames = llmClient.stream(chatRequest(), modelConfig).collectList().block();

        assertThat(frames).hasSize(1);
        assertThat(frames.get(0)).contains("\"error\":true");
        assertThat(frames.get(0)).contains("503");
    }

    @Test
    void stream_存量完整completionsURL不双重拼接() {
        // 存量数据 api_endpoint 可能存了完整 completions URL（真机 404 根因），须剥后缀再拼一次
        modelConfig.setApiEndpoint("https://open.bigmodel.cn/api/paas/v4/chat/completions");
        when(streamClient.stream(any(OpenAiCompatStreamClient.StreamRequest.class))).thenReturn(Flux.empty());

        llmClient.stream(chatRequest(), modelConfig).collectList().block();

        org.mockito.InOrder inOrder = org.mockito.Mockito.inOrder(streamClient);
        inOrder.verify(streamClient).stream(org.mockito.ArgumentMatchers.argThat(req ->
                "https://open.bigmodel.cn/api/paas/v4/chat/completions".equals(req.url())));
    }

    @Test
    void chat_成功时填充内容与模型名() {
        org.springframework.ai.chat.model.ChatResponse aiResp = aiResponse("回答内容");
        when(chatModel.call(any(Prompt.class))).thenReturn(aiResp);

        ChatResponse response = llmClient.chat(chatRequest(), modelConfig);

        assertThat(response.getSuccess()).isTrue();
        assertThat(response.getContent()).isEqualTo("回答内容");
        assertThat(response.getModelName()).isEqualTo("DeepSeek Chat");
    }

    @Test
    void chat_异常时返回友好错误() {
        when(chatModel.call(any(Prompt.class))).thenThrow(new RuntimeException("ReadTimeout"));

        ChatResponse response = llmClient.chat(chatRequest(), modelConfig);

        assertThat(response.getSuccess()).isFalse();
        assertThat(response.getErrorMessage()).contains("超时");
    }
}
