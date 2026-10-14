package com.forge.modules.ai.service;

import com.forge.modules.ai.dto.response.AgentConversationResponse;
import com.forge.modules.ai.entity.AiAgentRun;
import com.forge.modules.ai.entity.AiAgentRunStep;
import com.forge.modules.ai.entity.AiAgentTodo;

import java.util.List;
import java.util.Map;

/**
 * AI Agent 运行/待办服务
 */
public interface AiAgentRunService {

    /**
     * 分页查询运行记录（agentId/status 可选过滤）
     */
    Map<String, Object> pageRuns(Long agentId, String status, String triggerType, Long conversationId, int pageNum, int pageSize);

    /**
     * 当前用户的 Agent 会话列表（按 conversation 分组的运行记录）
     */
    List<AgentConversationResponse> listConversations();

    /**
     * 删除 Agent 会话：逻辑删除关联运行记录并删除会话本体
     */
    void deleteConversation(Long conversationId);

    List<AiAgentRunStep> getSteps(Long runId);

    AiAgentRun getRun(Long id);

    /**
     * 分页查询待办（status 可选过滤）
     */
    Map<String, Object> pageTodos(String status, int pageNum, int pageSize);

    /**
     * 确认/驳回待办并恢复执行；返回恢复后的运行状态
     */
    AiAgentRun handleTodo(Long todoId, boolean approved, String comment);
}
