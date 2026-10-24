package com.forge.modules.ai.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.forge.modules.ai.agent.tools.AgentTool;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

import java.util.Map;

/**
 * AgentTool → Spring AI ToolCallback 适配：工具定义（名称/描述/由 parameterSpec 生成的 JSON Schema）
 * 与执行（args JSON → Map → agentTool.execute）。internalToolExecutionEnabled=false 时框架不自动执行，
 * 由图路由（approval/tools 节点）接管，高危门控语义不变。
 */
public class AgentToolCallback implements ToolCallback {

    private final AgentTool agentTool;
    private final ObjectMapper objectMapper;
    private final ToolDefinition definition;

    public AgentToolCallback(AgentTool agentTool, ObjectMapper objectMapper) {
        this.agentTool = agentTool;
        this.objectMapper = objectMapper;
        this.definition = ToolDefinition.builder()
                .name(agentTool.name())
                .description(agentTool.description() + (agentTool.highRisk() ? "（高危，需人工确认）" : ""))
                .inputSchema(fromParameterSpec(agentTool.parameterSpec()))
                .build();
    }

    /**
     * parameterSpec（参数名→中文说明）生成 OpenAI tools 兼容的 JSON Schema（object + string 属性）
     */
    static String fromParameterSpec(Map<String, String> parameterSpec) {
        StringBuilder sb = new StringBuilder("{\"type\":\"object\",\"properties\":{");
        boolean first = true;
        for (Map.Entry<String, String> entry : parameterSpec.entrySet()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append("\"").append(entry.getKey()).append("\":{\"type\":\"string\",\"description\":\"")
                    .append(entry.getValue().replace("\"", "'")).append("\"}");
        }
        sb.append("},\"required\":[]}");
        return sb.toString();
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return definition;
    }

    @Override
    public String call(String toolInput) {
        try {
            Map<String, Object> args = toolInput == null || toolInput.isBlank()
                    ? Map.of()
                    : objectMapper.readValue(toolInput, new TypeReference<Map<String, Object>>() {
                    });
            Object result = agentTool.execute(args);
            return objectMapper.writeValueAsString(result == null ? Map.of() : result);
        } catch (Exception e) {
            return "{\"error\":\"" + e.getMessage() + "\"}";
        }
    }
}
