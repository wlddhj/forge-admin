package com.forge.admin.modules.system.dto.online;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 登录用户会话信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginUserSession implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 会话ID（tokenId）
     */
    private String tokenId;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 用户名
     */
    private String username;

    /**
     * 昵称
     */
    private String nickname;

    /**
     * 登录IP
     */
    private String loginIp;

    /**
     * 登录地点
     */
    private String loginLocation;

    /**
     * 浏览器
     */
    private String browser;

    /**
     * 操作系统
     */
    private String os;

    /**
     * 登录时间（时间戳）
     */
    private Long loginTime;

    /**
     * 最后活跃时间（时间戳）
     */
    private Long lastActiveTime;
}
