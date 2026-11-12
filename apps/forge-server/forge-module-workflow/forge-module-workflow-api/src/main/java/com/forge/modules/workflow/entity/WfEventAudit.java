package com.forge.modules.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 工作流事件审计
 *
 * @author forge-admin
 */
@Data
@TableName("wf_event_audit")
public class WfEventAudit {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 租户ID（非租户模式视作租户 1）
     */
    private Long tenantId;

    private String eventType;

    private Long instanceId;

    private Long taskId;

    private String taskName;

    private String operatorId;

    private String operatorName;

    private String summary;

    private LocalDateTime createTime;
}
