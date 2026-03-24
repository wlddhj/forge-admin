package com.forge.admin.modules.system.dto.config;

import lombok.Data;

@Data
public class ConfigQueryRequest {
    private String configName;
    private String configKey;
    private Integer status;
    private Integer pageNum = 1;
    private Integer pageSize = 10;
}
