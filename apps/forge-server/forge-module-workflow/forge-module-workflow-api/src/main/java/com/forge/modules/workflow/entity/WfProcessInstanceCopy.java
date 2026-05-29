package com.forge.modules.workflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 流程抄送记录实体
 *
 * @author forge
 */
@Data
@TableName("wf_process_instance_copy")
public class WfProcessInstanceCopy {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 发起人ID
     */
    private Long startUserId;

    /**
     * 流程实例名称
     */
    private String processInstanceName;

    /**
     * 流程实例ID
     */
    private String processInstanceId;

    /**
     * 流程编号
     */
    private String processNo;

    /**
     * 流程定义ID
     */
    private String processDefinitionId;

    /**
     * 流程分类
     */
    private String category;

    /**
     * 活动节点ID（BPMN XML中的节点编号）
     */
    private String activityId;

    /**
     * 活动节点名称
     */
    private String activityName;

    /**
     * 任务ID
     */
    private String taskId;

    /**
     * 被抄送的用户ID
     */
    private Long userId;

    /**
     * 抄送意见
     */
    private String reason;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT)
    private Long createBy;
}
