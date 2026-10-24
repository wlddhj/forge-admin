package com.forge.modules.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.forge.modules.ai.entity.AiAgentCheckpoint;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AiAgentCheckpointMapper extends BaseMapper<AiAgentCheckpoint> {

    /**
     * 检查点 upsert（复用 uk_thread_checkpoint 唯一键，替代 selectOne+update 两跳）
     */
    @Insert("INSERT INTO ai_agent_checkpoint (run_id, thread_id, checkpoint_id, node, next_node_id, state) "
            + "VALUES (#{e.runId}, #{e.threadId}, #{e.checkpointId}, #{e.node}, #{e.nextNodeId}, #{e.state}) "
            + "ON DUPLICATE KEY UPDATE node = VALUES(node), next_node_id = VALUES(next_node_id), state = VALUES(state)")
    int upsert(@Param("e") AiAgentCheckpoint entity);
}
