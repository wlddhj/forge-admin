package com.forge.admin.modules.auth.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.forge.admin.common.config.JwtProperties;
import com.forge.admin.common.config.JustAuthProperties;
import com.forge.admin.common.enums.SocialSource;
import com.forge.admin.common.exception.BusinessException;
import com.forge.admin.common.response.ResultCode;
import com.forge.admin.common.utils.IpUtils;
import com.forge.admin.modules.auth.dto.LoginResponse;
import com.forge.admin.modules.auth.dto.SocialUserResponse;
import com.forge.admin.modules.auth.entity.SysSocialUser;
import com.forge.admin.modules.auth.mapper.SysSocialUserMapper;
import com.forge.admin.modules.auth.security.JwtTokenProvider;
import com.forge.admin.modules.auth.service.RefreshTokenService;
import com.forge.admin.modules.auth.service.SocialAuthService;
import com.forge.admin.modules.system.dto.online.LoginUserSession;
import com.forge.admin.modules.system.entity.SysUser;
import com.forge.admin.modules.system.service.LoginUserSessionService;
import com.forge.admin.modules.system.service.SysLoginLogService;
import com.forge.admin.modules.system.service.SysUserService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.zhyd.oauth.model.AuthCallback;
import me.zhyd.oauth.model.AuthResponse;
import me.zhyd.oauth.model.AuthUser;
import me.zhyd.oauth.request.AuthRequest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * 社交登录服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SocialAuthServiceImpl implements SocialAuthService {

    private static final String TEMP_TOKEN_PREFIX = "social_temp:";
    private static final long TEMP_TOKEN_TTL_MINUTES = 10;

    private final Map<String, AuthRequest> authRequestMap;
    private final SysSocialUserMapper socialUserMapper;
    private final SysUserService sysUserService;
    private final JwtTokenProvider jwtTokenProvider;
    private final JwtProperties jwtProperties;
    private final RefreshTokenService refreshTokenService;
    private final LoginUserSessionService loginUserSessionService;
    private final SysLoginLogService sysLoginLogService;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public String getAuthorizeUrl(String source) {
        AuthRequest authRequest = getAuthRequest(source);
        String state = UUID.randomUUID().toString().replace("-", "");
        return authRequest.authorize(state);
    }

    @Override
    public LoginResponse handleCallback(String source, String code, String state, HttpServletRequest httpRequest) {
        AuthRequest authRequest = getAuthRequest(source);
        AuthCallback callback = AuthCallback.builder().code(code).state(state).build();

        @SuppressWarnings("unchecked")
        AuthResponse<AuthUser> response = authRequest.login(callback);
        if (!response.ok()) {
            log.error("社交登录失败: source={}, msg={}", source, response.getMsg());
            throw new BusinessException(ResultCode.SOCIAL_LOGIN_FAILED);
        }

        AuthUser authUser = response.getData();
        String openId = authUser.getUuid();

        // 查找绑定关系
        SysSocialUser socialUser = socialUserMapper.selectOne(
                new LambdaQueryWrapper<SysSocialUser>()
                        .eq(SysSocialUser::getSource, source)
                        .eq(SysSocialUser::getOpenId, openId)
        );

        if (socialUser == null || socialUser.getUserId() == null) {
            // 未绑定，生成临时token存 Redis
            saveTempSocialUser(source, authUser);
            return null;
        }

        // 已绑定，查找系统用户并颁发 token
        SysUser sysUser = sysUserService.getById(socialUser.getUserId());
        if (sysUser == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }
        if (sysUser.getStatus() != 1) {
            throw new BusinessException(ResultCode.USER_DISABLED);
        }

        // 更新社交用户信息
        updateSocialUserInfo(socialUser, authUser);

        // 颁发 JWT token
        LoginResponse loginResponse = issueToken(sysUser, httpRequest);

        // 记录登录日志
        String loginIp = IpUtils.getClientIp(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");
        sysLoginLogService.recordLoginLog(sysUser.getUsername(), 1,
                SocialSource.fromCode(source).getDisplayName() + "登录", loginIp, userAgent);

        log.info("社交登录成功: source={}, openId={}, username={}", source, openId, sysUser.getUsername());
        return loginResponse;
    }

    @Override
    public String getUnboundTempToken(String source, String code, String state) {
        AuthRequest authRequest = getAuthRequest(source);
        AuthCallback callback = AuthCallback.builder().code(code).state(state).build();

        @SuppressWarnings("unchecked")
        AuthResponse<AuthUser> response = authRequest.login(callback);
        if (!response.ok()) {
            log.error("获取社交用户信息失败: source={}, msg={}", source, response.getMsg());
            throw new BusinessException(ResultCode.SOCIAL_LOGIN_FAILED);
        }

        AuthUser authUser = response.getData();
        return saveTempSocialUser(source, authUser);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void bindSocialAccount(Long userId, String tempToken) {
        // 从 Redis 读取临时社交用户信息
        String key = TEMP_TOKEN_PREFIX + tempToken;
        String json = stringRedisTemplate.opsForValue().get(key);
        if (json == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "临时令牌已过期，请重新授权");
        }

        try {
            @SuppressWarnings("unchecked")
            Map<String, String> socialInfo = objectMapper.readValue(json, Map.class);
            String source = socialInfo.get("source");
            String openId = socialInfo.get("openId");

            // 检查是否已绑定
            Long count = socialUserMapper.selectCount(
                    new LambdaQueryWrapper<SysSocialUser>()
                            .eq(SysSocialUser::getSource, source)
                            .eq(SysSocialUser::getOpenId, openId)
            );
            if (count > 0) {
                throw new BusinessException(ResultCode.SOCIAL_USER_ALREADY_BOUND);
            }

            // 创建绑定记录
            SysSocialUser socialUser = new SysSocialUser();
            socialUser.setUserId(userId);
            socialUser.setSource(source);
            socialUser.setOpenId(openId);
            socialUser.setUnionId(socialInfo.get("unionId"));
            socialUser.setNickname(socialInfo.get("nickname"));
            socialUser.setAvatar(socialInfo.get("avatar"));
            socialUser.setRawUserInfo(socialInfo.get("rawUserInfo"));
            socialUser.setStatus(1);
            socialUserMapper.insert(socialUser);

            // 删除临时token
            stringRedisTemplate.delete(key);

            log.info("社交账号绑定成功: userId={}, source={}, openId={}", userId, source, openId);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("绑定社交账号失败", e);
            throw new BusinessException(ResultCode.FAILED, "绑定失败");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unbindSocialAccount(Long userId, String source) {
        int deleted = socialUserMapper.delete(
                new LambdaQueryWrapper<SysSocialUser>()
                        .eq(SysSocialUser::getUserId, userId)
                        .eq(SysSocialUser::getSource, source)
        );
        if (deleted == 0) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "未找到绑定记录");
        }
        log.info("社交账号解绑成功: userId={}, source={}", userId, source);
    }

    @Override
    public List<SocialUserResponse> listBindings(Long userId) {
        List<SysSocialUser> socialUsers = socialUserMapper.selectList(
                new LambdaQueryWrapper<SysSocialUser>()
                        .eq(SysSocialUser::getUserId, userId)
                        .orderByAsc(SysSocialUser::getSource)
        );

        List<SocialUserResponse> result = new ArrayList<>();
        for (SysSocialUser su : socialUsers) {
            SocialSource source = SocialSource.fromCode(su.getSource());
            result.add(SocialUserResponse.builder()
                    .id(su.getId())
                    .source(su.getSource())
                    .sourceName(source.getDisplayName())
                    .nickname(su.getNickname())
                    .avatar(su.getAvatar())
                    .bindTime(su.getCreateTime() != null ? su.getCreateTime().toString() : null)
                    .build());
        }
        return result;
    }

    // ========== 私有方法 ==========

    private AuthRequest getAuthRequest(String source) {
        AuthRequest authRequest = authRequestMap.get(source);
        if (authRequest == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "不支持的社交平台: " + source);
        }
        return authRequest;
    }

    /**
     * 保存临时社交用户信息到 Redis
     */
    private String saveTempSocialUser(String source, AuthUser authUser) {
        try {
            String tempToken = UUID.randomUUID().toString().replace("-", "");
            Map<String, String> info = new HashMap<>();
            info.put("source", source);
            info.put("openId", authUser.getUuid());
            info.put("unionId", authUser.getToken() != null ? authUser.getToken().getUnionId() : null);
            info.put("nickname", authUser.getNickname());
            info.put("avatar", authUser.getAvatar());
            info.put("rawUserInfo", objectMapper.writeValueAsString(authUser));

            stringRedisTemplate.opsForValue().set(
                    TEMP_TOKEN_PREFIX + tempToken,
                    objectMapper.writeValueAsString(info),
                    TEMP_TOKEN_TTL_MINUTES, TimeUnit.MINUTES
            );
            return tempToken;
        } catch (Exception e) {
            log.error("保存临时社交用户信息失败", e);
            throw new BusinessException(ResultCode.FAILED, "保存社交用户信息失败");
        }
    }

    /**
     * 颁发 JWT token（复用登录流程逻辑）
     */
    private LoginResponse issueToken(SysUser user, HttpServletRequest httpRequest) {
        String tokenId = UUID.randomUUID().toString().replace("-", "");
        String accessToken = jwtTokenProvider.generateTokenWithId(user.getUsername(), tokenId);
        String refreshToken = refreshTokenService.generateRefreshToken(
                user.getUsername(), jwtProperties.getRefreshExpiration()
        );

        // 保存登录会话
        String loginIp = IpUtils.getClientIp(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");
        long currentTime = System.currentTimeMillis();
        LoginUserSession session = LoginUserSession.builder()
                .tokenId(tokenId)
                .userId(user.getId())
                .username(user.getUsername())
                .nickname(user.getNickname())
                .loginIp(loginIp)
                .loginLocation(IpUtils.getLocationByIp(loginIp))
                .browser(IpUtils.getBrowser(userAgent))
                .os(IpUtils.getOs(userAgent))
                .loginTime(currentTime)
                .lastActiveTime(currentTime)
                .build();
        loginUserSessionService.saveSession(session, jwtProperties.getRefreshExpiration());

        // 更新最后登录信息
        user.setLastLoginTime(LocalDateTime.now());
        user.setLastLoginIp(loginIp);
        sysUserService.updateById(user);

        return LoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtProperties.getExpiration())
                .refreshExpiresIn(jwtProperties.getRefreshExpiration())
                .build();
    }

    /**
     * 更新社交用户信息
     */
    private void updateSocialUserInfo(SysSocialUser socialUser, AuthUser authUser) {
        try {
            socialUser.setNickname(authUser.getNickname());
            socialUser.setAvatar(authUser.getAvatar());
            socialUser.setRawUserInfo(objectMapper.writeValueAsString(authUser));
            if (authUser.getToken() != null) {
                socialUser.setAccessToken(authUser.getToken().getAccessToken());
                if (authUser.getToken().getExpireIn() > 0) {
                    socialUser.setTokenExpireTime(new Date(System.currentTimeMillis() + authUser.getToken().getExpireIn() * 1000L)
                            .toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime());
                }
            }
            socialUserMapper.updateById(socialUser);
        } catch (Exception e) {
            log.warn("更新社交用户信息失败", e);
        }
    }
}
