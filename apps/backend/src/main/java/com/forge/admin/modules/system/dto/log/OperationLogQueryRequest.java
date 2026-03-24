package com.forge.admin.modules.system.dto.log;

import lombok.Data;

@Data
public class OperationLogQueryRequest {
    private String title;
    private String operatorName;
    private String businessType;
    private Integer status;
    private String startTime;
    private String endTime;
    private Integer pageNum = 1;
    private Integer pageSize = 10;
}
