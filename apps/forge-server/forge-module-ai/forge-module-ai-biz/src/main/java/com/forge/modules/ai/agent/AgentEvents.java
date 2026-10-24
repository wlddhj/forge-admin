package com.forge.modules.ai.agent;

import java.util.Map;

/**
 * Agent 运行事件（SSE 推送与前端时间线渲染依据）
 */
public final class AgentEvents {

    public enum Type {
        STEP_START, STEP_END, TOOL_CALL, TOOL_RESULT, APPROVAL_REQUIRED, CONTENT, FINAL, ERROR,
        /** 决策流式思考增量（旧前端未识别自动忽略） */
        THOUGHT
    }

    public record AgentEvent(Type type, String node, String name, Map<String, Object> payload) {
    }

    private AgentEvents() {
    }
}
