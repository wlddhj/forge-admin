package com.forge.modules.system.service.app;

import com.forge.modules.system.dto.app.AppLoginResponse;

public interface AppAuthService {

    AppLoginResponse wxLogin(String tenantCode, String code);

    /**
     * 兼容旧调用（无租户上下文，仅限过渡期或测试使用）
     * 默认归到 id=1 的 default 租户
     */
    default AppLoginResponse wxLogin(String code) {
        return wxLogin("default", code);
    }

    AppLoginResponse refreshToken(String refreshToken);

    void logout(String accessToken, String refreshToken);
}
