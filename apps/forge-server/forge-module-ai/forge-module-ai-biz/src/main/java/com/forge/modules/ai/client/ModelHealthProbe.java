package com.forge.modules.ai.client;

import com.forge.modules.ai.entity.AiModelConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 模型可用性探测：向目标模型发送 max_tokens=1 的最小请求，2xx 即可用
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ModelHealthProbe {

    private final OpenAiChatModelFactory modelFactory;

    /**
     * 探测模型配置是否可用（密钥为空/端点无效/超时/401 均判不可用）
     */
    public boolean probe(AiModelConfig config) {
        if (!StringUtils.hasText(config.getApiEndpoint()) || !StringUtils.hasText(config.getApiKey())) {
            log.info("模型 {} 未配置端点或密钥，判定不可用", config.getModelName());
            return false;
        }
        try {
            OpenAiChatModel chatModel = modelFactory.get(config);
            Prompt prompt = new Prompt(
                    List.of(new UserMessage("ping")),
                    OpenAiChatOptions.builder()
                            .model(config.getModelCode())
                            .maxTokens(1)
                            .build());
            chatModel.call(prompt);
            return true;
        } catch (Exception e) {
            log.info("模型 {} 探测不可用: {}", config.getModelName(), e.getMessage());
            return false;
        }
    }
}
