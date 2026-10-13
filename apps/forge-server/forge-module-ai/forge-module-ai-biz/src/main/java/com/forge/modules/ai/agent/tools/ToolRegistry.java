package com.forge.modules.ai.agent.tools;

import com.forge.common.exception.BusinessException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 工具注册表：Spring Bean 自动收集
 */
@Component
public class ToolRegistry {

    private final Map<String, AgentTool> tools;

    public ToolRegistry(List<AgentTool> toolList) {
        this.tools = toolList.stream()
                .collect(Collectors.toMap(AgentTool::name, Function.identity()));
    }

    public AgentTool get(String name) {
        AgentTool tool = tools.get(name);
        if (tool == null) {
            throw new BusinessException("未注册的 Agent 工具: " + name);
        }
        return tool;
    }

    /**
     * 工具清单描述（注入决策节点提示词）
     */
    public String describeTools() {
        StringBuilder sb = new StringBuilder();
        for (AgentTool tool : tools.values()) {
            sb.append("- ").append(tool.name())
                    .append(tool.highRisk() ? "（高危，需人工确认）" : "")
                    .append(": ").append(tool.description());
            if (!tool.parameterSpec().isEmpty()) {
                sb.append(" 参数: ");
                tool.parameterSpec().forEach((k, v) -> sb.append(k).append("=").append(v).append("; "));
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    public boolean hasHighRisk(java.util.Collection<String> toolNames) {
        return toolNames.stream().map(tools::get).anyMatch(t -> t != null && t.highRisk());
    }
}
