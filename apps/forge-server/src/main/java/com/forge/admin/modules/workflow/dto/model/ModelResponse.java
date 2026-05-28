package com.forge.admin.modules.workflow.dto.model;

import lombok.Data;

import java.util.Date;

/**
 * 模型响应
 *
 * @author forge-admin
 */
@Data
public class ModelResponse {

    /**
     * 模型ID
     */
    private String id;

    /**
     * 模型名称
     */
    private String name;

    /**
     * 模型标识（流程定义Key）
     */
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
     * 版本号
     */
    private String version;

    /**
     * 扩展信息（JSON）
     */
    private String metaInfo;

    /**
     * 创建时间
     */
    private Date createTime;

    /**
     * 最后更新时间
     */
    private Date lastUpdateTime;

    /**
     * 是否已部署
     */
    private boolean deployed;

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
     * BPMN XML 内容（仅详情接口返回）
     */
    private String bpmnXml;
}
