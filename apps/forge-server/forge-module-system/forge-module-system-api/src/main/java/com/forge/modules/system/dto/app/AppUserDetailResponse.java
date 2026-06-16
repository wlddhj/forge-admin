package com.forge.modules.system.dto.app;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * App用户详情响应（后台管理用）
 *
 * @author forge
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AppUserDetailResponse {
    /**
     * 用户ID
     */
    private Long id;

    /**
     * 昵称
     */
    private String nickname;

    /**
     * 头像
     */
    private String avatar;

    /**
     * 手机号（已脱敏）
     */
    private String phone;

    /**
     * 手机号验证状态
     */
    private Integer phoneVerified;

    /**
     * 微信OpenID（已脱敏）
     */
    private String openId;

    /**
     * 微信UnionID（已脱敏）
     */
    private String unionId;

    /**
     * 用户状态
     */
    private Integer status;

    /**
     * 最后登录时间
     */
    private LocalDateTime lastLoginTime;

    /**
     * 注销时间
     */
    private LocalDateTime deactivatedTime;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}