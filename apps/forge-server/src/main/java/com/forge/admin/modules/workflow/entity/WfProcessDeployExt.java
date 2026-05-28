package com.forge.admin.modules.workflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 流程部署扩展信息实体
 *
 * @author forge-admin
 */
@Data
@TableName("wf_process_deploy_ext")
public class WfProcessDeployExt {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 部署ID
     */
    private String deploymentId;

    /**
     * 流程定义ID
     */
    private String processDefinitionId;

    /**
     * 流程定义Key
     */
    private String processKey;

    /**
     * 流程名称
     */
    @TableField(insertStrategy = FieldStrategy.ALWAYS)
    private String processName;

    /**
     * 流程分类ID
     */
    private Long categoryId;

    /**
     * 描述
     */
    private String description;

    /**
     * 表单Key
     */
    private String formKey;

    /**
     * 表单类型(10流程表单 20业务表单)
     */
    private Integer formType;

    /**
     * 关联表单ID
     */
    private Long formId;

    /**
     * 自动抄送策略
     */
    private Integer autoCopyStrategy;

    /**
     * 自动抄送参数
     */
    private String autoCopyParam;

    /**
     * BPMN XML内容
     */
    private String bpmnXml;

    /**
     * 部署人ID
     */
    private Long createBy;

    /**
     * 部署人名称
     */
    private String createByName;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /**
     * 删除标记
     */
    @TableLogic
    private Integer deleted;
}
