package com.forge.modules.ai.agent.kernel;

import com.forge.modules.ai.agent.AgentEvents;
import com.forge.modules.ai.agent.tools.ToolRegistry;
import com.forge.modules.ai.entity.AiAgentConfig;
import com.forge.modules.ai.entity.AiModelConfig;
import lombok.Data;

import java.util.Map;
import java.util.function.Consumer;

/**
 * 图构建与运行上下文：配置、步骤模型绑定、工具与事件回调
 */
@Data
public class AgentGraphContext {

    private final AiAgentConfig agentConfig;

    /** 节点名 → 模型配置（model_bindings 解析结果） */
    private final Map<String, AiModelConfig> nodeModels;

    private final ToolRegistry toolRegistry;

    private final Consumer<AgentEvents.AgentEvent> emitter;

    private final java.util.concurrent.atomic.AtomicInteger stepSeq;

    public void emit(AgentEvents.Type type, String node, String name, Map<String, Object> payload) {
        emitter.accept(new AgentEvents.AgentEvent(type, node, name, payload));
    }

    public int nextSeq() {
        return stepSeq.incrementAndGet();
    }
}
