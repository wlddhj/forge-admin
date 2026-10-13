package com.forge.modules.ai.agent.kernel;

import org.bsc.langgraph4j.state.AgentState;

import java.util.Map;

/**
 * Agent 图状态：所有值必须 JSON 可序列化（图片只存 URL 引用）
 */
public class AgentGraphState extends AgentState {

    public static final String INPUT = "input";
    public static final String IMAGES = "images";
    public static final String IMAGE_TEXT = "imageText";
    public static final String DECISION = "decision";
    public static final String TOOL_HISTORY = "toolHistory";
    public static final String APPROVAL = "approval";
    public static final String FINAL_ANSWER = "finalAnswer";
    public static final String STEPS = "steps";

    public AgentGraphState(Map<String, Object> data) {
        super(data);
    }
}
