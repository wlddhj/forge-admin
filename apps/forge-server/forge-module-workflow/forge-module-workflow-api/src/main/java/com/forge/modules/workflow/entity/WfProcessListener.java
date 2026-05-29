package com.forge.modules.workflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 流程监听器实体
 *
 * @author forge
 */
@Data
@TableName("wf_process_listener")
public class WfProcessListener {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 监听器名称
     */
    private String name;

    /**
     * 状态(0:禁用 1:启用)
     */
    private Integer status;

    /**
     * 监听类型(execution/task)
     */
    private String type;

    /**
     * 监听事件
     * execution: start, end
     * task: create, assignment, complete, delete
     */
    private String event;

    /**
     * 值类型(class/delegateExpression/expression)
     */
    private String valueType;

    /**
     * 值（类名、表达式或Bean名称）
     */
    private String value;

    /**
     * 备注
     */
    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableField(fill = FieldFill.INSERT)
    private Long createBy;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updateBy;

    @TableLogic
    private Integer deleted;
}
