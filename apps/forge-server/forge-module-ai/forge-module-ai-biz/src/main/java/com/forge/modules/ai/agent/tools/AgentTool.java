package com.forge.modules.ai.agent.tools;

import java.util.Map;

/**
 * Agent 工具 SPI：实现类注册为 Spring Bean 自动纳入 ToolRegistry
 */
public interface AgentTool {

    String name();

    String description();

    /**
     * 参数说明（注入决策节点提示词），键为参数名、值为说明文案
     */
    Map<String, String> parameterSpec();

    /**
     * 是否高危（高危工具调用前需人工确认）
     */
    default boolean highRisk() {
        return false;
    }

    /**
     * 执行工具；抛出异常视为工具失败（决策节点可感知重试或转直接回答）
     */
    Object execute(Map<String, Object> args);
}
