package com.forge.admin.modules.system.dto.log;

import lombok.Data;

@Data
public class LoginLogQueryRequest {
    private String username;
    private String loginIp;
    private Integer status;
    private String startTime;
    private String endTime;
    private Integer pageNum = 1;
    private Integer pageSize = 10;
}
