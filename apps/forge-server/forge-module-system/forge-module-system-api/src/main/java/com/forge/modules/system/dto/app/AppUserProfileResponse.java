package com.forge.modules.system.dto.app;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * App用户资料响应
 *
 * @author forge
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "App用户资料响应")
public class AppUserProfileResponse {

    /**
     * 用户ID
     */
    @Schema(description = "用户ID")
    private Long id;

    /**
     * 昵称
     */
    @Schema(description = "昵称")
    private String nickname;

    /**
     * 头像
     */
    @Schema(description = "头像")
    private String avatar;

    /**
     * 手机号（已脱敏）
     */
    @Schema(description = "手机号（已脱敏）")
    private String phone;

    /**
     * 手机号验证状态
     */
    @Schema(description = "手机号验证状态")
    private Integer phoneVerified;

    /**
     * 微信OpenID（脱敏：前4后4）
     */
    @Schema(description = "微信OpenID（脱敏：前4后4）")
    private String openId;

    /**
     * 用户状态
     */
    @Schema(description = "用户状态")
    private Integer status;

    /**
     * 最后登录时间
     */
    @Schema(description = "最后登录时间")
    private LocalDateTime lastLoginTime;
}
