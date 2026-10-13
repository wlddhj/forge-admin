package com.forge.modules.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI Agent 运行步骤明细实体（仅插入，无逻辑删除）
 */
@Data
@TableName("ai_agent_run_step")
public class AiAgentRunStep {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long runId;

    private Integer seq;

    private String nodeName;

    private String stepType;

    private Long modelId;

    private String toolName;

    private String stepInput;

    private String stepOutput;

    private Integer tokensIn;

    private Integer tokensOut;

    private Integer durationMs;

    private String status;

    private String errorMsg;

    private Long tenantId;

    private LocalDateTime createTime;
}
