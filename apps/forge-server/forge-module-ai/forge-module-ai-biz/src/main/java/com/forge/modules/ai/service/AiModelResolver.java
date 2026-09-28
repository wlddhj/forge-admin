package com.forge.modules.ai.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.forge.modules.ai.config.AiModuleConfig;
import com.forge.modules.ai.dto.request.ChatRequest;
import com.forge.modules.ai.entity.AiModelConfig;
import com.forge.modules.ai.mapper.AiModelConfigMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 模型解析器：modelId → modelName/modelCode → 默认模型 逐级回退
 */
@Component
@RequiredArgsConstructor
public class AiModelResolver {

    private final AiModelConfigMapper modelConfigMapper;
    private final AiModuleConfig aiModuleConfig;

    /**
     * 解析请求对应的模型配置；无任何可用模型时返回 null
     */
    public AiModelConfig resolve(ChatRequest request) {
        if (request.getModelId() != null) {
            AiModelConfig config = modelConfigMapper.selectById(request.getModelId());
            if (config != null && config.getStatus() == 1) {
                return config;
            }
        }

        String modelKey = request.getModelName() != null ? request.getModelName()
                : aiModuleConfig.getModels().getDefaultModel();
        if (modelKey != null) {
            AiModelConfig byName = modelConfigMapper.selectOne(new LambdaQueryWrapper<AiModelConfig>()
                    .and(w -> w.eq(AiModelConfig::getModelName, modelKey)
                            .or()
                            .eq(AiModelConfig::getModelCode, modelKey))
                    .eq(AiModelConfig::getStatus, 1)
                    .last("LIMIT 1"));
            if (byName != null) {
                return byName;
            }
        }

        return resolveDefault();
    }

    /**
     * 默认模型（isDefault=1），否则任一启用模型
     */
    public AiModelConfig resolveDefault() {
        AiModelConfig defaultConfig = modelConfigMapper.selectOne(new LambdaQueryWrapper<AiModelConfig>()
                .eq(AiModelConfig::getIsDefault, 1)
                .eq(AiModelConfig::getStatus, 1)
                .last("LIMIT 1"));
        if (defaultConfig != null) {
            return defaultConfig;
        }
        List<AiModelConfig> enabled = modelConfigMapper.selectList(new LambdaQueryWrapper<AiModelConfig>()
                .eq(AiModelConfig::getStatus, 1)
                .last("LIMIT 1"));
        return enabled.isEmpty() ? null : enabled.get(0);
    }
}
