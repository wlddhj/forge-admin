package com.forge.admin.modules.workflow.dto.definition;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 流程部署请求
 *
 * @author forge-admin
 */
@Data
public class ProcessDeployRequest {

    /**
     * 流程名称
     */
    @NotBlank(message = "流程名称不能为空")
    private String name;

    /**
     * 流程Key
     */
    @NotBlank(message = "流程Key不能为空")
    private String key;

    /**
     * 分类ID
     */
    private Long categoryId;

    /**
     * 描述
     */
    private String description;

    /**
     * BPMN XML内容
     */
    @NotBlank(message = "BPMN XML内容不能为空")
    private String bpmnXml;

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
}
