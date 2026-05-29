package com.forge.modules.workflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 表单管理实体
 *
 * @author forge
 */
@Data
@TableName("wf_form")
public class WfForm {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 表单名称
     */
    private String name;

    /**
     * 状态(0:禁用 1:启用)
     */
    private Integer status;

    /**
     * 表单配置(JSON)
     */
    private String conf;

    /**
     * 表单字段(JSON)
     */
    private String fields;

    /**
     * 备注
     */
    private String remark;

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
     * 创建人
     */
    @TableField(fill = FieldFill.INSERT)
    private Long createBy;

    /**
     * 更新人
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updateBy;

    /**
     * 删除标记
     */
    @TableLogic
    private Integer deleted;
}
