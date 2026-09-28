package com.forge.modules.ai.client;

import com.forge.modules.ai.config.AiModuleConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.web.reactive.function.client.WebClient;

import static org.assertj.core.api.Assertions.assertThat;

class OpenAiChatModelFactoryTest {

    private OpenAiChatModelFactory factory;

    @BeforeEach
    void setUp() {
        factory = new OpenAiChatModelFactory(WebClient.builder(), new AiModuleConfig());
    }

    private com.forge.modules.ai.entity.AiModelConfig config(String apiKey) {
        com.forge.modules.ai.entity.AiModelConfig config = new com.forge.modules.ai.entity.AiModelConfig();
        config.setId(1L);
        config.setModelName("DeepSeek Chat");
        config.setModelCode("deepseek-chat");
        config.setApiEndpoint("https://api.deepseek.com/v1/chat/completions");
        config.setApiKey(apiKey);
        return config;
    }

    @Test
    void 同配置返回同一实例_配置变更重建() {
        OpenAiChatModel first = factory.get(config("sk-a"));
        OpenAiChatModel second = factory.get(config("sk-a"));
        OpenAiChatModel changed = factory.get(config("sk-b"));

        assertThat(first).isSameAs(second);
        assertThat(changed).isNotSameAs(first);
    }

    @Test
    void evict后强制重建() {
        OpenAiChatModel first = factory.get(config("sk-a"));
        factory.evict(1L);
        OpenAiChatModel second = factory.get(config("sk-a"));
        assertThat(second).isNotSameAs(first);
    }

    @Test
    void 存量完整completionsURL被规范化为baseurl() {
        // 不触发网络请求，仅验证构建成功（normalizeBaseUrl 不抛异常）
        OpenAiChatModel model = factory.get(config("sk-a"));
        assertThat(model).isNotNull();
    }
}
