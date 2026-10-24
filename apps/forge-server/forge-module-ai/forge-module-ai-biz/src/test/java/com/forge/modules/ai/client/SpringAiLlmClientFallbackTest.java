package com.forge.modules.ai.client;

import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 模型故障转移单测：主模型失败自动切换同类型备用模型
 */
@ExtendWith(MockitoExtension.class)
class SpringAiLlmClientFallbackTest {

    @Mock
    private OpenAiChatModelFactory modelFactory;
    @Mock
    private AiModelConfigMapper modelConfigMapper;
    @Mock
    private SysConfigService sysConfigService;

    private SpringAiLlmClient client;

    private final AiModelConfig primary = model(1L, "主模型");
    private final AiModelConfig backup = model(2L, "备用模型");

    @BeforeEach
    void setUp() {
        com.forge.modules.ai.config.AiModuleConfig aiModuleConfig = new com.forge.modules.ai.config.AiModuleConfig();
        client = new SpringAiLlmClient(modelFactory, new ObjectMapper(), modelConfigMapper, sysConfigService,
                aiModuleConfig, java.util.concurrent.Executors.newFixedThreadPool(2),
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
        request.setContent("hi");
        return request;
    }

    private void mockModelCall(AiModelConfig config, boolean success) {
        OpenAiChatModel model = mock(OpenAiChatModel.class);
        lenient().when(modelFactory.get(config)).thenReturn(model);
        if (success) {
            org.springframework.ai.chat.model.ChatResponse aiResponse = new org.springframework.ai.chat.model.ChatResponse(
                    List.of(new Generation(new AssistantMessage("ok"))));
            lenient().when(model.call(any(Prompt.class))).thenReturn(aiResponse);
        } else {
            lenient().when(model.call(any(Prompt.class)))
                    .thenThrow(new RuntimeException("connect timeout"));
        }
    }

    @Test
    void 主模型成功_不触发备用查询() {
        mockModelCall(primary, true);

        ChatResponse response = client.chat(request(), primary);

        assertThat(response.getSuccess()).isTrue();
        assertThat(response.getModelName()).isEqualTo("主模型");
    }

    @Test
    void 主模型失败_切换备用成功() {
        mockModelCall(primary, false);
        mockModelCall(backup, true);
        when(modelConfigMapper.selectList(any())).thenReturn(List.of(backup));

        ChatResponse response = client.chat(request(), primary);

        assertThat(response.getSuccess()).isTrue();
        assertThat(response.getModelName()).isEqualTo("备用模型");
    }

    @Test
    void 开关关闭_失败直接返回主模型响应() {
        when(sysConfigService.getConfigValueByKey("ai.model.fallback-enabled")).thenReturn("0");
        mockModelCall(primary, false);

        ChatResponse response = client.chat(request(), primary);

        assertThat(response.getSuccess()).isFalse();
        assertThat(response.getModelName()).isEqualTo("主模型");
    }

    @Test
    void 备用全失败_返回主模型原始失败() {
        mockModelCall(primary, false);
        mockModelCall(backup, false);
        when(modelConfigMapper.selectList(any())).thenReturn(List.of(backup));

        ChatResponse response = client.chat(request(), primary);

        assertThat(response.getSuccess()).isFalse();
        assertThat(response.getModelName()).isEqualTo("主模型");
        // friendlyError 会将原始异常转义为友好文案，断言失败原因非空
        assertThat(response.getErrorMessage()).isNotBlank();
    }
}
