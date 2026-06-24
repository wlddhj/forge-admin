package com.forge.modules.workflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 流程定义扩展信息实体 - FlowLong 版本
 * 对应表 wf_process_ext
 *
 * @author forge-admin
 */
@Data
@TableName("wf_process_ext")
public class WfProcessExt {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * FlowLong 流程定义ID（关联 flw_process.id）
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long processId;

    /**
     * 流程标识
     */
    private String processKey;

    /**
     * 流程名称
     */
    @TableField(insertStrategy = FieldStrategy.ALWAYS)
    private String processName;

    /**
     * 分类ID（关联 wf_category.id）
     */
    private Long categoryId;

    /**
     * 流程描述
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
     * FlowLong 流程模型 JSON 内容
     */
    @TableField("model_json")
    private String modelJson;

    /**
     * 元信息 JSON（存储表单配置等扩展信息）
     */
    @TableField("meta_info")
    private String metaInfo;

    /**
     * 创建人ID
     */
    private Long createBy;

    /**
     * 创建人名称
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