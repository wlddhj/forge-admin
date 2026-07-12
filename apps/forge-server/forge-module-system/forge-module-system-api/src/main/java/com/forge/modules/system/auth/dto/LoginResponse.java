package com.forge.modules.system.auth.dto;

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
     * 租户ID（前端可不再解码 JWT，直接用此值填 X-Tenant-Id 请求头）
     */
    @Schema(description = "租户ID", example = "1")
    private Long tenantId;

    /**
     * 租户标识（登录时使用的 tenantCode 原样回显）
     */
    @Schema(description = "租户标识", example = "default")
    private String tenantCode;

    /**
     * 租户名称（用于前端展示）
     */
    @Schema(description = "租户名称", example = "默认租户")
    private String tenantName;

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

    /**
     * 是否需要修改密码（首次登录或密码过期）
     */
    @Schema(description = "是否需要修改密码", example = "false")
    private Boolean needChangePassword;

    /**
     * 提示消息
     */
    @Schema(description = "提示消息", example = "首次登录请修改密码")
    private String message;

    /**
     * 密码剩余有效天数（null 表示不检查或无过期限制）
     */
    @Schema(description = "密码剩余有效天数", example = "85")
    private Integer passwordExpireDays;

    /**
     * 密码是否已过期（true 时前端应跳转改密页）
     */
    @Schema(description = "密码是否已过期", example = "false")
    private Boolean passwordExpired;
}
