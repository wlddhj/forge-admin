package com.forge.admin.modules.auth.dto;

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

    @Schema(description = "密码", example = "admin123", required = true)
    @NotBlank(message = "密码不能为空")
    private String password;
}
