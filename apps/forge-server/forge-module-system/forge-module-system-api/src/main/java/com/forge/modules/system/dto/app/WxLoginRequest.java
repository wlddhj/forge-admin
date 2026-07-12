package com.forge.modules.system.dto.app;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class WxLoginRequest {

    @NotBlank(message = "租户标识不能为空")
    private String tenantCode;

    @NotBlank(message = "微信登录code不能为空")
    private String code;
}
