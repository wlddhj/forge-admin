package com.forge.admin.modules.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 刷新 Token 请求
 *
 * @author standadmin
 */
@Schema(description = "刷新 Token 请求")
@Data
public class RefreshTokenRequest {

    @NotBlank(message = "刷新令牌不能为空")
    @Schema(description = "刷新令牌", example = "abc123...xyz789")
    private String refreshToken;
}
