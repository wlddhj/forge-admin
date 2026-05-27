package com.forge.admin.modules.workflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 流程表达式实体
 *
 * @author forge
 */
@Data
@TableName("wf_process_expression")
public class WfProcessExpression {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 表达式名称
     */
    private String name;

    /**
     * 状态(0:禁用 1:启用)
     */
    private Integer status;

    /**
     * 表达式内容
     */
    private String expression;

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
