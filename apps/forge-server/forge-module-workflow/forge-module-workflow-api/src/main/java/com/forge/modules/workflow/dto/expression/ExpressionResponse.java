package com.forge.modules.workflow.dto.expression;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ExpressionResponse {

    private Long id;
    private String name;
    private Integer status;
    private String expression;
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
