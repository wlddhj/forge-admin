package com.forge.admin.modules.system.dto.log;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class OperationLogResponse {
    private Long id;
    private String title;
    private String businessType;
    private String requestMethod;
    private String requestUrl;
    private Long operatorId;
    private String operatorName;
    private String deptName;
    private String operateIp;
    private String operateLocation;
    private String requestParam;
    private String jsonResult;
    private Integer status;
    private String errorMsg;
    private LocalDateTime operateTime;
    private Long costTime;
}
