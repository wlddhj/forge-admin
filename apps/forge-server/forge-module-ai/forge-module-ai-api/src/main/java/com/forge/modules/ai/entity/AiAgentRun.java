package com.forge.modules.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.forge.framework.tenant.core.db.TenantBaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * AI Agent 运行记录实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_agent_run")
public class AiAgentRun extends TenantBaseDO {

    public static final String STATUS_RUNNING = "RUNNING";
    public static final String STATUS_WAITING_APPROVAL = "WAITING_APPROVAL";
    public static final String STATUS_SUCCEEDED = "SUCCEEDED";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STATUS_CANCELLED = "CANCELLED";

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long agentId;

    /** 团队协作运行时指向 ai_agent_team.id（此时 agentId 为空） */
    private Long teamId;

    private Long conversationId;

    private Long userId;

    private String triggerType;

    private String status;

    private String lastNode;

    private String errorMsg;

    private String inputSummary;

    private String finalAnswer;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    @TableLogic
    private Integer deleted;

    /** 团队名称（查询时填充，非表字段） */
    @com.baomidou.mybatisplus.annotation.TableField(exist = false)
    private String teamName;
}
