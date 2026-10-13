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
 * AI Agent 人工介入待办实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_agent_todo")
public class AiAgentTodo extends TenantBaseDO {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_APPROVED = "APPROVED";
    public static final String STATUS_REJECTED = "REJECTED";

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long runId;

    private String title;

    private String content;

    private String status;

    private Long handlerId;

    private String handlerName;

    private String handleComment;

    private LocalDateTime handleTime;

    @TableLogic
    private Integer deleted;
}
