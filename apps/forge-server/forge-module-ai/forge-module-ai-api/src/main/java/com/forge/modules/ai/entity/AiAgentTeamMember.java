package com.forge.modules.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.forge.framework.tenant.core.db.TenantBaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * AI Agent 团队成员实体：引用 ai_agent_config 行，附职责与执行约束
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_agent_team_member")
public class AiAgentTeamMember extends TenantBaseDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long teamId;

    private Long agentId;

    /** 职责描述（注入 Supervisor 路由提示词） */
    private String memberRole;

    /** 工具白名单 JSON 数组（空=继承全部工具） */
    private String allowedTools;

    /** 专家内最大循环步数（NULL=取 Agent 配置） */
    private Integer maxSteps;

    private Integer sortOrder;

    @TableLogic
    private Integer deleted;
}
