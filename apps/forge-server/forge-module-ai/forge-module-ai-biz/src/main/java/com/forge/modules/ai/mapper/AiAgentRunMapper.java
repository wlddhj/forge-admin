package com.forge.modules.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.forge.modules.ai.dto.response.AgentConversationResponse;
import com.forge.modules.ai.entity.AiAgentRun;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface AiAgentRunMapper extends BaseMapper<AiAgentRun> {

    /**
     * 当前用户的 Agent 对话列表：按会话聚合运行次数，取最近 100 个会话
     */
    List<AgentConversationResponse> selectAgentConversations(Long userId);
}
