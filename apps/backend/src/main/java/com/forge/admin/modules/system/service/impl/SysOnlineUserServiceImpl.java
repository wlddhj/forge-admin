package com.forge.admin.modules.system.service.impl;

import com.forge.admin.modules.system.dto.online.LoginUserSession;
import com.forge.admin.modules.system.dto.online.OnlineUserResponse;
import com.forge.admin.modules.system.service.LoginUserSessionService;
import com.forge.admin.modules.system.service.SysOnlineUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 在线用户服务实现
 */
@Service
@RequiredArgsConstructor
public class SysOnlineUserServiceImpl implements SysOnlineUserService {

    private final LoginUserSessionService loginUserSessionService;

    @Override
    public List<OnlineUserResponse> getOnlineUsers() {
        List<LoginUserSession> sessions = loginUserSessionService.getAllSessions();

        return sessions.stream().map(session -> {
            OnlineUserResponse user = new OnlineUserResponse();
            user.setTokenId(session.getTokenId());
            user.setUserId(session.getUserId());
            user.setUsername(session.getUsername());
            user.setNickname(session.getNickname());
            user.setLoginIp(session.getLoginIp());
            user.setLoginLocation(session.getLoginLocation());
            user.setBrowser(session.getBrowser());
            user.setOs(session.getOs());
            user.setLoginTime(session.getLoginTime());

            // 获取剩余过期时间
            Long ttl = loginUserSessionService.getSessionTTL(session.getTokenId());
            user.setTtl(ttl != null && ttl > 0 ? ttl : 0);

            return user;
        }).collect(Collectors.toList());
    }

    @Override
    public void forceLogout(String tokenId) {
        loginUserSessionService.deleteSession(tokenId);
    }
}
