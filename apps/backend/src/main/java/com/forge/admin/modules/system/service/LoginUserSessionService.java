package com.forge.admin.modules.system.service;

import com.forge.admin.modules.system.dto.online.LoginUserSession;

import java.util.List;

/**
 * 登录用户会话服务
 */
public interface LoginUserSessionService {

    /**
     * 保存登录会话
     *
     * @param session 会话信息
     * @param ttl     过期时间（毫秒）
     */
    void saveSession(LoginUserSession session, long ttl);

    /**
     * 获取所有在线用户（超过30分钟无心跳的自动过滤）
     */
    List<LoginUserSession> getAllSessions();

    /**
     * 获取会话剩余过期时间（秒）
     */
    Long getSessionTTL(String tokenId);

    /**
     * 删除会话（强制下线）
     */
    void deleteSession(String tokenId);

    /**
     * 更新会话最后活跃时间（心跳）
     *
     * @param tokenId 会话ID
     */
    void updateLastActiveTime(String tokenId);
}
