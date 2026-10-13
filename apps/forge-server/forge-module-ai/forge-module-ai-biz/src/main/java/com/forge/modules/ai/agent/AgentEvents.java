package com.forge.modules.ai.agent;

import java.util.Map;

/**
 * Agent 运行事件（SSE 推送与前端时间线渲染依据）
 */
public final class AgentEvents {

    public enum Type {
        STEP_START, STEP_END, TOOL_CALL, TOOL_RESULT, APPROVAL_REQUIRED, FINAL, ERROR
    }

    public record AgentEvent(Type type, String node, String name, Map<String, Object> payload) {
    }

    private AgentEvents() {
    }
}
