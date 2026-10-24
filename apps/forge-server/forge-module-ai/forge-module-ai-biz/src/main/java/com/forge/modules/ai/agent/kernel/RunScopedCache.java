package com.forge.modules.ai.agent.kernel;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.forge.modules.ai.entity.AiAgentConfig;
import com.forge.modules.ai.entity.AiModelConfig;
import com.forge.modules.ai.mapper.AiAgentConfigMapper;
import com.forge.modules.ai.mapper.AiModelConfigMapper;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * run 级配置缓存：Agent/模型配置在一次运行内不变，图节点循环内重复查库改为首次加载后复用
 * （computeIfAbsent 天然去重并发首查）。生命周期 = 单次 AgentGraphRunner.execute。
 */
public class RunScopedCache {

    private final AiAgentConfigMapper agentConfigMapper;
    private final AiModelConfigMapper modelConfigMapper;

    private final Map<Long, AiAgentConfig> agents = new ConcurrentHashMap<>();
    private final Map<Long, AiModelConfig> models = new ConcurrentHashMap<>();
    private final Map<String, AiModelConfig> modelsByType = new ConcurrentHashMap<>();

    public RunScopedCache(AiAgentConfigMapper agentConfigMapper, AiModelConfigMapper modelConfigMapper) {
        this.agentConfigMapper = agentConfigMapper;
        this.modelConfigMapper = modelConfigMapper;
    }

    public AiAgentConfig agent(Long id) {
        if (id == null) {
            return null;
        }
        return agents.computeIfAbsent(id, agentConfigMapper::selectById);
    }

    public AiModelConfig model(Long id) {
        if (id == null) {
            return null;
        }
        return models.computeIfAbsent(id, modelConfigMapper::selectById);
    }

    /** 按模型类型取首个启用模型（modelType=..., status=1，按 id 升序） */
    public AiModelConfig modelByType(String modelType) {
        if (modelType == null || modelType.isBlank()) {
            return null;
        }
        return modelsByType.computeIfAbsent(modelType, type -> modelConfigMapper.selectList(
                        new LambdaQueryWrapper<AiModelConfig>()
                                .eq(AiModelConfig::getModelType, type)
                                .eq(AiModelConfig::getStatus, 1)
                                .orderByAsc(AiModelConfig::getId)
                                .last("LIMIT 1"))
                .stream().findFirst().orElse(null));
    }
}
