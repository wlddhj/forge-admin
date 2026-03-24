package com.forge.admin.modules.system.dto.config;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ConfigRequest {
    private Long id;

    @NotBlank(message = "配置名称不能为空")
    private String configName;

    @NotBlank(message = "配置键不能为空")
    private String configKey;

    private String configValue;
    private String configType = "text";
    private String configGroup = "system";
    private String remark;
}
