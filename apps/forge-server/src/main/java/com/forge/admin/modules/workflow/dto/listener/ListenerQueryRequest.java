package com.forge.admin.modules.workflow.dto.listener;

import lombok.Data;

@Data
public class ListenerQueryRequest {

    private Integer pageNum = 1;
    private Integer pageSize = 10;
    private String name;
    private String type;
    private Integer status;
}
