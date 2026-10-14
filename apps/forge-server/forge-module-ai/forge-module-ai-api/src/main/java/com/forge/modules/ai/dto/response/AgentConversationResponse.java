package com.forge.modules.ai.dto.response;

import lombok.Data;

/**
 * Agent 会话列表项（按 conversation 分组的运行记录）
 */
@Data
public class AgentConversationResponse {

    private Long conversationId;

    private String title;

    private Long runCount;

    private Long lastRunId;

    private String lastTime;
}
