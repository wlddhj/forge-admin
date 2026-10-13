package com.forge.modules.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI Agent 检查点实体（LangGraph4j checkpoint 持久化，仅插入/查询）
 */
@Data
@TableName("ai_agent_checkpoint")
public class AiAgentCheckpoint {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long runId;

    private String threadId;

    private String checkpointId;

    private String parentId;

    private String node;

    private String nextNodeId;

    private String state;

    private Long tenantId;

    private LocalDateTime createTime;
}
