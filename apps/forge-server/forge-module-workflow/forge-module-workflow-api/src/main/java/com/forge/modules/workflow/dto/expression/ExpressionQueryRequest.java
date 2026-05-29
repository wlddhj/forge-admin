package com.forge.modules.workflow.dto.expression;

import lombok.Data;

@Data
public class ExpressionQueryRequest {

    private Integer pageNum = 1;
    private Integer pageSize = 10;
    private String name;
    private Integer status;
}
