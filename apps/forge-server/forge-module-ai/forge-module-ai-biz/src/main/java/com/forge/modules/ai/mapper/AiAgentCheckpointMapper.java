package com.forge.modules.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.forge.modules.ai.entity.AiAgentCheckpoint;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AiAgentCheckpointMapper extends BaseMapper<AiAgentCheckpoint> {

    /**
     * 检查点 upsert（复用 uk_thread_checkpoint 唯一键，替代 selectOne+update 两跳）
     */
    int upsert(@Param("e") AiAgentCheckpoint entity);
}
