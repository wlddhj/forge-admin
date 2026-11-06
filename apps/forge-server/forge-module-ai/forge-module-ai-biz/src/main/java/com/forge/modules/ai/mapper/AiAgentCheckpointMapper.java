package com.forge.modules.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.forge.modules.ai.entity.AiAgentCheckpoint;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AiAgentCheckpointMapper extends BaseMapper<AiAgentCheckpoint> {

    /**
     * 检查点 upsert（复用 uk_thread_checkpoint 唯一键，替代 selectOne+update 两跳）。
     * ⚠VALUES() 在 MySQL 8.0.20+ 已标记弃用，替代写法「VALUES(...) AS new 行别名」被租户插件
     * TenantLineInnerInterceptor 的 JSqlParser（项目锁 4.9，行别名语法 5.0 才支持）解析即
     * ParseException——TENANT_ENABLE=true 时运行时报错，升级 jsqlparser 5.x 前禁改（见 AiUpsertSqlParseGuardTest）。
     */
    @Insert("INSERT INTO ai_agent_checkpoint (run_id, thread_id, checkpoint_id, node, next_node_id, state) "
            + "VALUES (#{e.runId}, #{e.threadId}, #{e.checkpointId}, #{e.node}, #{e.nextNodeId}, #{e.state}) "
            + "ON DUPLICATE KEY UPDATE node = VALUES(node), next_node_id = VALUES(next_node_id), state = VALUES(state)")
    int upsert(@Param("e") AiAgentCheckpoint entity);
}
