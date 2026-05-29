package com.forge.modules.workflow.dto.definition;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Date;

/**
 * 流程定义响应
 *
 * @author forge-admin
 */
@Data
public class ProcessDefinitionResponse {

    /**
     * 流程定义ID
     */
    private String id;

    /**
     * 流程定义Key
     */
    private String key;

    /**
     * 流程名称
     */
    private String name;

    /**
     * 版本号
     */
    private Integer version;

    /**
     * 分类ID
     */
    private Long categoryId;

    /**
     * 分类名称
     */
    private String categoryName;

    /**
     * 部署ID
     */
    private String deploymentId;

    /**
     * 挂起状态（1激活 2挂起）
     */
    private Integer suspensionState;

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
     * 资源名称
     */
    private String resourceName;

    /**
     * 流程图资源名称
     */
    private String diagramResourceName;

    /**
     * 创建时间（部署时间）
     */
    private LocalDateTime createTime;

    /**
     * 部署人名称
     */
    private String deployUserName;

    /**
     * BPMN XML内容
     */
    private String bpmnXml;
}
