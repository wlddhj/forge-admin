package com.forge.modules.ai.agent.kernel;

import org.bsc.langgraph4j.StateGraph;

/**
 * Agent 编排图定义（代码定义）：graph_key 对应一个实现
 */
public interface AgentGraphDefinition {

    String graphKey();

    /**
     * 构建图结构（节点动作内通过 ctx 访问模型/工具/事件）
     */
    StateGraph<AgentGraphState> build(AgentGraphContext ctx) throws org.bsc.langgraph4j.GraphStateException;
}
