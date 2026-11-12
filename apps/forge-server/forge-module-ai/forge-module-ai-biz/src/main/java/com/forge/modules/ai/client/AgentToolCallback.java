package com.forge.modules.ai.client;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.forge.modules.ai.agent.tools.AgentTool;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

import java.util.LinkedHashMap;
import java.util.List;
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
     * parameterSpec（参数名→中文说明）生成 OpenAI tools 兼容的 JSON Schema（object + string 属性）。
     * 经 ObjectMapper 序列化（键/值转义天然正确）；LinkedHashMap 容忍 null 键值且保持字段序。
     */
    String fromParameterSpec(Map<String, String> parameterSpec) {
        Map<String, Object> properties = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : parameterSpec.entrySet()) {
            Map<String, Object> property = new LinkedHashMap<>();
            property.put("type", "string");
            property.put("description", entry.getValue());
            properties.put(entry.getKey(), property);
        }
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", List.of());
        return objectMapper.writeValueAsString(schema);
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
            // 错误 JSON 同样经 ObjectMapper 序列化（message 转义天然正确）；
            // String.valueOf 兜底 null message，避免 Map.of 空值 NPE
            return objectMapper.writeValueAsString(Map.of("error", String.valueOf(e.getMessage())));
        }
    }
}
