package com.forge.modules.ai.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.forge.modules.ai.dto.request.ChatRequest;
import com.forge.modules.ai.dto.response.ChatResponse;
import com.forge.modules.ai.entity.AiModelConfig;
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

@ExtendWith(MockitoExtension.class)
class SpringAiLlmClientTest {

    @Mock
    private OpenAiChatModelFactory modelFactory;
    @Mock
    private OpenAiChatModel chatModel;

    private SpringAiLlmClient llmClient;
    private AiModelConfig modelConfig;

    @BeforeEach
    void setUp() {
        llmClient = new SpringAiLlmClient(modelFactory, new ObjectMapper());
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

    @Test
    void stream_产出前端SSE契约帧序列_content帧加DONE() {
        when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.just(
                aiResponse("你好"),
                aiResponse("世界")));

        ChatRequest request = new ChatRequest();
        request.setContent("hi");

        List<String> frames = llmClient.stream(request, modelConfig).collectList().block();

        assertThat(frames).containsExactly(
                "{\"content\":\"你好\"}",
                "{\"content\":\"世界\"}",
                "[DONE]");
    }

    @Test
    void stream_换行符正确JSON转义_不破坏SSE帧() {
        when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.just(
                aiResponse("第一行\n第二行")));

        ChatRequest request = new ChatRequest();
        request.setContent("hi");

        List<String> frames = llmClient.stream(request, modelConfig).collectList().block();

        assertThat(frames.get(0)).isEqualTo("{\"content\":\"第一行\\n第二行\"}");
        assertThat(frames).doesNotContain("\n第一行");
    }

    @Test
    void stream_空文本chunk被跳过() {
        when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.just(
                aiResponse(""),
                aiResponse("有效")));

        ChatRequest request = new ChatRequest();
        request.setContent("hi");

        List<String> frames = llmClient.stream(request, modelConfig).collectList().block();

        assertThat(frames).containsExactly("{\"content\":\"有效\"}", "[DONE]");
    }

    @Test
    void stream_上游异常输出error帧_无DONE() {
        when(chatModel.stream(any(Prompt.class)))
                .thenReturn(Flux.error(new RuntimeException("Connection refused: api.deepseek.com")));

        ChatRequest request = new ChatRequest();
        request.setContent("hi");

        List<String> frames = llmClient.stream(request, modelConfig).collectList().block();

        assertThat(frames).hasSize(1);
        assertThat(frames.get(0)).contains("\"error\":true");
        assertThat(frames.get(0)).contains("无法连接模型服务端点");
    }

    @Test
    void chat_成功时填充内容与模型名() {
        org.springframework.ai.chat.model.ChatResponse aiResp = aiResponse("回答内容");
        when(chatModel.call(any(Prompt.class))).thenReturn(aiResp);

        ChatRequest request = new ChatRequest();
        request.setContent("hi");

        ChatResponse response = llmClient.chat(request, modelConfig);

        assertThat(response.getSuccess()).isTrue();
        assertThat(response.getContent()).isEqualTo("回答内容");
        assertThat(response.getModelName()).isEqualTo("DeepSeek Chat");
    }

    @Test
    void chat_异常时返回友好错误() {
        when(chatModel.call(any(Prompt.class))).thenThrow(new RuntimeException("ReadTimeout"));

        ChatRequest request = new ChatRequest();
        request.setContent("hi");

        ChatResponse response = llmClient.chat(request, modelConfig);

        assertThat(response.getSuccess()).isFalse();
        assertThat(response.getErrorMessage()).contains("超时");
    }
}
