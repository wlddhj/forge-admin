package com.forge.admin.modules.system.dto.job;

import lombok.Data;

@Data
public class JobQueryRequest {
    private String jobName;
    private String jobGroup;
    private Integer status;
    private Integer pageNum = 1;
    private Integer pageSize = 10;
}
