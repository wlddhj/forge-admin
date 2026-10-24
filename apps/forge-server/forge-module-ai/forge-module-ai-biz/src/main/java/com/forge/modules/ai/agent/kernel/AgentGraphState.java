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
    /** 团队协作：批量分派队列 [{agentId, agentCode, agentName, task, maxSteps, allowedTools}]（并行专家池消费） */
    public static final String DISPATCH_QUEUE = "dispatchQueue";
    /** 团队协作：当前分派轮次（子图检查点 threadId 组成部分，保证中断恢复的子图 thread 稳定） */
    public static final String EXPERT_ROUND = "expertRound";
    /** 多轮记忆：同会话近期成功 run 的摘要（决策/调度提示词注入） */
    public static final String CONVERSATION_MEMORY = "conversationMemory";

    public AgentGraphState(Map<String, Object> data) {
        super(data);
    }
}
