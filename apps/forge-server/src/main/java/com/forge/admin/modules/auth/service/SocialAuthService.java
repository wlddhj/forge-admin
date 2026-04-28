package com.forge.admin.modules.auth.service;

import com.forge.admin.modules.auth.dto.LoginResponse;
import com.forge.admin.modules.auth.dto.SocialUserResponse;

import java.util.List;

/**
 * 社交登录服务接口
 */
public interface SocialAuthService {

    /**
     * 生成第三方授权URL
     *
     * @param source 平台标识（wechat/dingtalk）
     * @return 授权URL
     */
    String getAuthorizeUrl(String source);

    /**
     * 处理OAuth2回调
     *
     * @param source 平台标识
     * @param code   授权码
     * @param state  状态参数
     * @param httpRequest HTTP请求
     * @return 登录响应（已绑定时），null 表示未绑定（需要先绑定账号）
     */
    LoginResponse handleCallback(String source, String code, String state, jakarta.servlet.http.HttpServletRequest httpRequest);

    /**
     * 获取未绑定社交用户的临时token（回调时使用）
     *
     * @param source 平台标识
     * @param code   授权码
     * @param state  状态参数
     * @return 临时token
     */
    String getUnboundTempToken(String source, String code, String state);

    /**
     * 绑定社交账号到当前登录用户
     *
     * @param userId    系统用户ID
     * @param tempToken 临时token
     */
    void bindSocialAccount(Long userId, String tempToken);

    /**
     * 解绑社交账号
     *
     * @param userId 系统用户ID
     * @param source 平台标识
     */
    void unbindSocialAccount(Long userId, String source);

    /**
     * 获取用户已绑定的社交账号列表
     *
     * @param userId 系统用户ID
     * @return 绑定列表
     */
    List<SocialUserResponse> listBindings(Long userId);
}
