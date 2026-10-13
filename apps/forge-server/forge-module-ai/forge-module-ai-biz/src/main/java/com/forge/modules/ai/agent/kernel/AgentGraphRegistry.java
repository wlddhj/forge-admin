package com.forge.modules.ai.agent.kernel;

import com.forge.common.exception.BusinessException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 编排图注册表：graph_key → 图定义
 */
@Component
public class AgentGraphRegistry {

    private final Map<String, AgentGraphDefinition> definitions;

    public AgentGraphRegistry(List<AgentGraphDefinition> definitionList) {
        this.definitions = definitionList.stream()
                .collect(Collectors.toMap(AgentGraphDefinition::graphKey, Function.identity()));
    }

    public AgentGraphDefinition get(String graphKey) {
        AgentGraphDefinition definition = definitions.get(graphKey);
        if (definition == null) {
            throw new BusinessException("未找到编排图定义: " + graphKey);
        }
        return definition;
    }
}
