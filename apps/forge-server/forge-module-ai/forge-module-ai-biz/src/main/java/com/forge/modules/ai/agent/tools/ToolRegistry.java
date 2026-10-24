package com.forge.modules.ai.agent.tools;

import com.forge.common.exception.BusinessException;
import org.springframework.stereotype.Component;

import java.util.Collection;
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

    /** 带执行上下文的工具执行（不感知上下文的工具经 default 委托保持原行为） */
    public Object execute(String name, Map<String, Object> args, ToolExecutionContext context) {
        return get(name).execute(args, context);
    }

    /** 全部注册工具（原生 tool-callback 构建用） */
    public Collection<AgentTool> all() {
        return tools.values();
    }

    /**
     * 工具清单描述（注入决策节点提示词）
     */
    public String describeTools() {
        return describeTools(null);
    }

    /**
     * 工具清单描述（按白名单过滤；null/空集合 = 全部工具）
     */
    public String describeTools(java.util.Collection<String> allowed) {
        StringBuilder sb = new StringBuilder();
        for (AgentTool tool : tools.values()) {
            if (allowed != null && !allowed.isEmpty() && !allowed.contains(tool.name())) {
                continue;
            }
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

    /**
     * 工具是否在白名单内（null/空白名单 = 允许全部）
     */
    public boolean isAllowed(String name, java.util.Collection<String> allowed) {
        return allowed == null || allowed.isEmpty() || allowed.contains(name);
    }

    public boolean hasHighRisk(java.util.Collection<String> toolNames) {
        return toolNames.stream().map(tools::get).anyMatch(t -> t != null && t.highRisk());
    }
}
