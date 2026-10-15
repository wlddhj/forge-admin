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

    /** 团队协作：团队 ID */
    public static final String TEAM_ID = "teamId";
    /** 团队协作：当前分派 {agentId, agentCode, agentName, task, maxSteps, allowedTools} */
    public static final String CURRENT_DISPATCH = "currentDispatch";
    /** 团队协作：各专家汇报累积 [{agent, task, output}] */
    public static final String AGENT_REPORTS = "agentReports";
    /** 团队协作：当前专家内循环步数 */
    public static final String EXPERT_STEPS = "expertSteps";
    /** 团队协作：专家审批结论（独立于单 Agent 的 APPROVAL，避免驳回后污染后续专家审批） */
    public static final String EXPERT_APPROVAL = "expertApproval";
    /** 团队协作：Supervisor 给出的最终回答（summary 节点直出，不重复调模型） */
    public static final String SUPERVISOR_ANSWER = "supervisorAnswer";

    public AgentGraphState(Map<String, Object> data) {
        super(data);
    }
}
