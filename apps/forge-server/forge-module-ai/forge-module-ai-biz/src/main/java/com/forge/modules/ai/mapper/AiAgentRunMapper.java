package com.forge.modules.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.forge.modules.ai.dto.response.AgentConversationResponse;
import com.forge.modules.ai.entity.AiAgentRun;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface AiAgentRunMapper extends BaseMapper<AiAgentRun> {

    @Select("""
            SELECT r.conversation_id AS conversationId,
                   c.title AS title,
                   COUNT(*) AS runCount,
                   MAX(r.id) AS lastRunId,
                   MAX(r.create_time) AS lastTime
            FROM ai_agent_run r
            LEFT JOIN ai_chat_conversation c ON r.conversation_id = c.id AND c.deleted = 0
            WHERE r.user_id = #{userId}
              AND r.trigger_type = 'CONVERSATION'
              AND r.conversation_id IS NOT NULL
              AND r.deleted = 0
            GROUP BY r.conversation_id, c.title
            ORDER BY lastRunId DESC
            LIMIT 100
            """)
    List<AgentConversationResponse> selectAgentConversations(Long userId);
}
