package com.forge.modules.workflow.dto.form;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 表单请求
 *
 * @author forge
 */
@Data
public class FormRequest {

    /**
     * 表单ID（更新时必传）
     */
    private Long id;

    /**
     * 表单名称
     */
    @NotBlank(message = "表单名称不能为空")
    private String name;

    /**
     * 状态(0:禁用 1:启用)
     */
    private Integer status = 1;

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
}
