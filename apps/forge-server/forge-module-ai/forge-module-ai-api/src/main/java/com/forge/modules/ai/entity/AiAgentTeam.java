package com.forge.modules.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.forge.framework.tenant.core.db.TenantBaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * AI Agent 团队实体：Supervisor 调度式多 Agent 协作配置
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_agent_team")
public class AiAgentTeam extends TenantBaseDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String teamCode;

    private String teamName;

    private String description;

    /** Supervisor 系统提示词（含路由规则与输出协议说明） */
    private String supervisorPrompt;

    /** {"supervisor":{modelId|modelType},"summary":{...}} */
    private String modelBindings;

    private Integer maxRounds;

    private Integer status;

    private String remark;

    @TableLogic
    private Integer deleted;
}
