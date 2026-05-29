package com.forge.modules.system.service;

import com.forge.modules.system.dto.online.OnlineUserResponse;

import java.util.List;

/**
 * 在线用户服务接口
 */
public interface SysOnlineUserService {

    /**
     * 获取在线用户列表
     */
    List<OnlineUserResponse> getOnlineUsers();

    /**
     * 强制下线
     */
    void forceLogout(String tokenId);
}
