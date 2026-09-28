package com.forge.modules.ai.service;

import com.forge.modules.ai.config.AiModuleConfig;
import com.forge.modules.ai.dto.request.ChatRequest;
import com.forge.modules.ai.entity.AiModelConfig;
import com.forge.modules.ai.mapper.AiModelConfigMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiModelResolverTest {

    @Mock
    private AiModelConfigMapper mapper;

    private AiModelResolver resolver;

    private final AiModelConfig defaultModel = model(1L, "DeepSeek Chat", "deepseek-chat", 1);

    @BeforeEach
    void setUp() {
        resolver = new AiModelResolver(mapper, new AiModuleConfig());
    }

    private AiModelConfig model(Long id, String name, String code, int isDefault) {
        AiModelConfig config = new AiModelConfig();
        config.setId(id);
        config.setModelName(name);
        config.setModelCode(code);
        config.setStatus(1);
        config.setIsDefault(isDefault);
        return config;
    }

    @Test
    void modelId优先命中() {
        when(mapper.selectById(2L)).thenReturn(model(2L, "GLM", "glm-4-flash", 0));

        ChatRequest request = new ChatRequest();
        request.setModelId(2L);

        assertThat(resolver.resolve(request).getId()).isEqualTo(2L);
    }

    @Test
    void modelId对应模型禁用时回退() {
        AiModelConfig disabled = model(2L, "GLM", "glm-4-flash", 0);
        disabled.setStatus(0);
        when(mapper.selectById(2L)).thenReturn(disabled);
        lenient().when(mapper.selectOne(any())).thenReturn(defaultModel);

        ChatRequest request = new ChatRequest();
        request.setModelId(2L);

        assertThat(resolver.resolve(request).getId()).isEqualTo(1L);
    }

    @Test
    void 无modelId无modelName_用配置默认模型() {
        when(mapper.selectOne(any())).thenReturn(defaultModel);

        ChatRequest request = new ChatRequest();
        request.setContent("hi");

        assertThat(resolver.resolve(request).getModelCode()).isEqualTo("deepseek-chat");
    }

    @Test
    void 无任何可用模型返回null() {
        when(mapper.selectOne(any())).thenReturn(null);
        when(mapper.selectList(any())).thenReturn(Collections.emptyList());

        ChatRequest request = new ChatRequest();

        assertThat(resolver.resolve(request)).isNull();
    }

    @Test
    void 无默认模型时取任一启用模型() {
        AiModelConfig fallback = model(9L, "Qwen", "qwen-plus", 0);
        when(mapper.selectOne(any())).thenReturn(null, fallback);
        when(mapper.selectList(any())).thenReturn(List.of(fallback));

        AiModelConfig resolved = resolver.resolveDefault();

        assertThat(resolved.getId()).isEqualTo(9L);
    }
}
