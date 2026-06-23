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
     * 分类ID
     */
    private Long categoryId;

    /**
     * 分类编码
     */
    private String category;

    /**
     * 描述
     */
    private String description;

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
     * 扩展信息（JSON）
     */
    private String metaInfo;

    /**
     * FlowLong 流程模型 JSON 内容
     * 由 FlowLong 流程设计器直接导出
     */
    private String modelJson;
}