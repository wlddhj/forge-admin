package com.forge.admin.modules.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 登录响应
 *
 * @author standadmin
 */
@Schema(description = "用户登录响应")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {

    /**
     * 访问令牌
     */
    @Schema(description = "访问令牌（JWT）", example = "eyJhbGciOiJIUzI1NiJ9...")
    private String accessToken;

    /**
     * 刷新令牌
     */
    @Schema(description = "刷新令牌", example = "abc123...xyz789")
    private String refreshToken;

    /**
     * Token 类型
     */
    @Schema(description = "Token 类型", example = "Bearer")
    private String tokenType;

    /**
     * 过期时间（毫秒）
     */
    @Schema(description = "Token 过期时间（毫秒）", example = "7200000")
    private Long expiresIn;

    /**
     * 刷新令牌过期时间（毫秒）
     */
    @Schema(description = "刷新令牌过期时间（毫秒）", example = "604800000")
    private Long refreshExpiresIn;
}
