package com.forge.modules.workflow.dto.form;

import lombok.Data;

/**
 * 表单简要响应（用于下拉选择）
 *
 * @author forge
 */
@Data
public class FormSimpleResponse {

    private Long id;

    /**
     * 表单名称
     */
    private String name;
}
