package com.forge.admin.modules.system.dto.config;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class ConfigResponse {
    private Long id;
    private String configName;
    private String configKey;
    private String configValue;
    private String configType;
    private String configGroup;
    private Integer isSystem;
    private String remark;
    private LocalDateTime createTime;
}
