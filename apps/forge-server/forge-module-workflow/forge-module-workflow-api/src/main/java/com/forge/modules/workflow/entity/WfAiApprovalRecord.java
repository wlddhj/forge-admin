package com.forge.modules.workflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI审批记录实体
 *
 * @author forge-admin
 */
@Data
@TableName("wf_ai_approval_record")
public class WfAiApprovalRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 流程实例ID
     */
    private Long processInstanceId;

    /**
     * 任务ID
     */
    private Long taskId;

    /**
     * 任务定义Key
     */
    private String taskDefKey;

    /**
     * 任务名称
     */
    private String taskName;

    /**
     * AI决策结果（APPROVE/REJECT/MANUAL）
     */
    private String decision;

    /**
     * 置信度
     */
    private Integer confidence;

    /**
     * AI分析说明
     */
    private String reasoning;

    /**
     * 原始AI响应
     */
    @TableField(typeHandler = com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler.class)
    private String rawResponse;

    /**
     * 执行状态
     */
    private String status;

    /**
     * AI模型提供商
     */
    private String provider;

    /**
     * AI模型名称
     */
    private String modelName;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}