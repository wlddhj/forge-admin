package com.forge.modules.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.forge.framework.tenant.core.db.TenantBaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * AI Agent 定时任务实体（联动 sys_job 调度）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_agent_job")
public class AiAgentJob extends TenantBaseDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String jobName;

    private String agentCode;

    /** 团队编码（与 agentCode 二选一，配了则调度走团队协作图） */
    private String teamCode;

    private Long sysJobId;

    private String inputTemplate;

    private String cronExpression;

    private Integer status;

    private Long lastRunId;

    @TableLogic
    private Integer deleted;
}
