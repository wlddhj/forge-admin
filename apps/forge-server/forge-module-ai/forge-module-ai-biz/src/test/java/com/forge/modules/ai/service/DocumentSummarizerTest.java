package com.forge.modules.ai.service;

import com.forge.modules.ai.client.LlmClient;
import com.forge.modules.ai.dto.request.ChatRequest;
import com.forge.modules.ai.dto.response.ChatResponse;
import com.forge.modules.ai.dto.response.DocumentResponse;
import com.forge.modules.ai.entity.AiModelConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentSummarizerTest {

    @Mock
    private LlmClient llmClient;

    private DocumentSummarizer summarizer;
    private AiModelConfig modelConfig;

    @BeforeEach
    void setUp() {
        summarizer = new DocumentSummarizer(llmClient);
        modelConfig = new AiModelConfig();
        modelConfig.setModelName("DeepSeek Chat");
        modelConfig.setTemperature(new BigDecimal("0.7"));
    }

    @Test
    void 成功时返回摘要与status1() {
        ChatResponse chatResponse = new ChatResponse();
        chatResponse.setSuccess(true);
        chatResponse.setContent("这是摘要");
        when(llmClient.chat(any(ChatRequest.class), any(AiModelConfig.class))).thenReturn(chatResponse);

        DocumentResponse response = summarizer.summarize("文档内容", "brief", 500, modelConfig);

        assertThat(response.getStatus()).isEqualTo(1);
        assertThat(response.getSummary()).isEqualTo("这是摘要");
    }

    @Test
    void prompt组装_系统消息与固定参数() {
        ChatResponse chatResponse = new ChatResponse();
        chatResponse.setSuccess(true);
        chatResponse.setContent("ok");
        when(llmClient.chat(any(ChatRequest.class), any(AiModelConfig.class))).thenReturn(chatResponse);

        summarizer.summarize("文档内容", "bullet", 300, modelConfig);

        ArgumentCaptor<ChatRequest> captor = ArgumentCaptor.forClass(ChatRequest.class);
        org.mockito.Mockito.verify(llmClient).chat(captor.capture(), org.mockito.Mockito.eq(modelConfig));
        ChatRequest request = captor.getValue();

        assertThat(request.getContent()).startsWith("以下是需要总结的内容:").contains("文档内容");
        assertThat(request.getMessages()).hasSize(1);
        assertThat(request.getMessages().get(0).getRole()).isEqualTo("system");
        assertThat(request.getMessages().get(0).getContent()).contains("要点列表").contains("300");
        assertThat(request.getTemperature()).isEqualTo(0.5);
        assertThat(request.getMaxTokens()).isEqualTo(600);
    }

    @Test
    void 未知风格回退brief() {
        ChatResponse chatResponse = new ChatResponse();
        chatResponse.setSuccess(true);
        chatResponse.setContent("ok");
        when(llmClient.chat(any(ChatRequest.class), any(AiModelConfig.class))).thenReturn(chatResponse);

        summarizer.summarize("text", "unknown-style", 500, modelConfig);

        ArgumentCaptor<ChatRequest> captor = ArgumentCaptor.forClass(ChatRequest.class);
        org.mockito.Mockito.verify(llmClient).chat(captor.capture(), org.mockito.Mockito.eq(modelConfig));
        assertThat(captor.getValue().getMessages().get(0).getContent()).contains("核心要点");
    }

    @Test
    void LLM失败时返回status2与错误信息() {
        ChatResponse chatResponse = new ChatResponse();
        chatResponse.setSuccess(false);
        chatResponse.setErrorMessage("密钥无效");
        when(llmClient.chat(any(ChatRequest.class), any(AiModelConfig.class))).thenReturn(chatResponse);

        DocumentResponse response = summarizer.summarize("text", "brief", 500, modelConfig);

        assertThat(response.getStatus()).isEqualTo(2);
        assertThat(response.getErrorMessage()).isEqualTo("密钥无效");
    }
}
