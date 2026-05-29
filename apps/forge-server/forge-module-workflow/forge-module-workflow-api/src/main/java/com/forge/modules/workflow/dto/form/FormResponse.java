package com.forge.modules.workflow.dto.form;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 表单响应
 *
 * @author forge
 */
@Data
public class FormResponse {

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
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}
