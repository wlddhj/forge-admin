package com.forge.modules.system.service;

import com.forge.modules.system.dto.online.LoginUserSession;

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

    /**
     * 获取指定用户的所有活跃会话
     *
     * @param username 用户名
     * @return 会话列表（按登录时间倒序）
     */
    List<LoginUserSession> getSessionsByUsername(String username);

    /**
     * 踢掉指定用户的所有会话（排除当前 tokenId）
     * 用于单点登录场景
     *
     * @param username        用户名
     * @param excludeTokenId  保留的当前会话ID（可为 null 表示全部踢出）
     * @return 被踢出的会话数量
     */
    int kickOutUserSessions(String username, String excludeTokenId);
}
