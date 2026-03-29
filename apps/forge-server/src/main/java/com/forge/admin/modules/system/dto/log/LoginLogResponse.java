package com.forge.admin.modules.system.dto.log;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class LoginLogResponse {
    private Long id;
    private String username;
    private String loginIp;
    private String loginLocation;
    private String browser;
    private String os;
    private Integer status;
    private String msg;
    private LocalDateTime loginTime;
}
