package com.forge.modules.workflow.dto.copy;

import lombok.Data;

@Data
public class CopyQueryRequest {

    private Integer pageNum = 1;
    private Integer pageSize = 10;
    private String processInstanceName;
    private Long userId;
}
