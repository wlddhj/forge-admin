package com.forge.admin.modules.workflow.dto.expression;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ExpressionRequest {

    private Long id;

    @NotBlank(message = "表达式名称不能为空")
    private String name;

    private Integer status;

    @NotBlank(message = "表达式内容不能为空")
    private String expression;

    private String remark;
}
