package com.forge.admin.modules.system.dto.online;

import lombok.Data;

@Data
public class OnlineUserResponse {
    private String tokenId;
    private Long userId;
    private String username;
    private String nickname;
    private String loginIp;
    private String loginLocation;
    private String browser;
    private String os;
    private Long loginTime;
    private Long lastActiveTime;
    private Long ttl;
    /**
     * 状态：online-在线，idle-闲置
     */
    private String status;
}
