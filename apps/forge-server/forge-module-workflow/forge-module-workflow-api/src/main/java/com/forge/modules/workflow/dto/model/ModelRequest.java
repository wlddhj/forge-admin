package com.forge.modules.workflow.dto.model;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 模型创建/更新请求
 *
 * @author forge-admin
 */
@Data
public class ModelRequest {

    /**
     * 模型ID（更新时必填）
     */
    private String id;

    /**
     * 模型名称
     */
    @NotBlank(message = "模型名称不能为空")
    private String name;

    /**
     * 模型标识（流程定义Key）
     */
    @NotBlank(message = "模型标识不能为空")
    private String key;

    /**
     * 分类编码
     */
    private String category;

    /**
     * 描述
     */
    private String description;

    /**
     * 扩展信息（JSON，包含 formType、formId）
     */
    private String metaInfo;

    /**
     * BPMN XML 内容（更新模型设计时使用）
     */
    private String bpmnXml;
}
