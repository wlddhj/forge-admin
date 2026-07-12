package com.forge.modules.system.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 登录请求
 *
 * @author standadmin
 */
@Schema(description = "用户登录请求")
@Data
public class LoginRequest {

    @Schema(description = "用户名", example = "admin", required = true)
    @NotBlank(message = "用户名不能为空")
    private String username;

    @Schema(description = "密码", example = "GoodPass#2026", required = true)
    @NotBlank(message = "密码不能为空")
    private String password;

    @Schema(description = "验证码ID", example = "abc123")
    private String captchaId;

    @Schema(description = "验证码", example = "ABCD")
    private String captchaCode;

    @Schema(description = "租户标识（启用多租户时必填；关闭时可省略）")
    private String tenantCode;
}
