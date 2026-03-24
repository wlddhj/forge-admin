package com.forge.admin.modules.auth.controller;

import com.forge.admin.common.annotation.RateLimiter;
import com.forge.admin.common.config.JwtProperties;
import com.forge.admin.common.response.Result;
import com.forge.admin.common.utils.IpUtils;
import com.forge.admin.common.utils.UserContext;
import com.forge.admin.modules.auth.dto.LoginRequest;
import com.forge.admin.modules.auth.dto.LoginResponse;
import com.forge.admin.modules.auth.dto.RefreshTokenRequest;
import com.forge.admin.modules.auth.dto.UserInfoResponse;
import com.forge.admin.modules.auth.security.JwtTokenProvider;
import com.forge.admin.modules.auth.service.RefreshTokenService;
import com.forge.admin.modules.system.dto.menu.MenuTreeResponse;
import com.forge.admin.modules.system.dto.online.LoginUserSession;
import com.forge.admin.modules.system.entity.SysUser;
import com.forge.admin.modules.system.service.LoginUserSessionService;
import com.forge.admin.modules.system.service.SysLoginLogService;
import com.forge.admin.modules.system.service.SysMenuService;
import com.forge.admin.modules.system.service.SysUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 认证控制器
 *
 * @author standadmin
 */
@Slf4j
@Tag(name = "认证管理")
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final JwtProperties jwtProperties;
    private final SysUserService sysUserService;
    private final SysMenuService sysMenuService;
    private final SysLoginLogService sysLoginLogService;
    private final RefreshTokenService refreshTokenService;
    private final LoginUserSessionService loginUserSessionService;

        @Operation(summary = "登录")
    @PostMapping("/login")
    @RateLimiter(keyType = RateLimiter.KeyType.USERNAME, time = 60, count = 20, message = "登录请求过于频繁，请稍后再试")
        public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        try {
            // 认证
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
            );

            // 获取用户信息
            SysUser user = sysUserService.getByUsername(request.getUsername());
            if (user == null) {
                return Result.failed("用户不存在");
            }

            // 生成 tokenId
            String tokenId = java.util.UUID.randomUUID().toString().replace("-", "");

            // 生成 Access Token（使用相同的 tokenId 关联会话）
            String accessToken = jwtTokenProvider.generateTokenWithId(request.getUsername(), tokenId);

            // 生成 Refresh Token
            String refreshToken = refreshTokenService.generateRefreshToken(
                    request.getUsername(),
                    jwtProperties.getRefreshExpiration()
            );

            // 获取客户端信息
            String loginIp = com.forge.admin.common.utils.IpUtils.getClientIp(httpRequest);
            String userAgent = httpRequest.getHeader("User-Agent");
            String browser = com.forge.admin.common.utils.IpUtils.getBrowser(userAgent);
            String os = com.forge.admin.common.utils.IpUtils.getOs(userAgent);
            String loginLocation = com.forge.admin.common.utils.IpUtils.getLocationByIp(loginIp);

            // 保存登录会话到 Redis
            LoginUserSession session = LoginUserSession.builder()
                    .tokenId(tokenId)
                    .userId(user.getId())
                    .username(user.getUsername())
                    .nickname(user.getNickname())
                    .loginIp(loginIp)
                    .loginLocation(loginLocation)
                    .browser(browser)
                    .os(os)
                    .loginTime(System.currentTimeMillis())
                    .build();
            loginUserSessionService.saveSession(session, jwtProperties.getRefreshExpiration());

            // 记录登录成功日志
            sysLoginLogService.recordLoginLog(request.getUsername(), 1, "登录成功", httpRequest);

            LoginResponse response = LoginResponse.builder()
                    .accessToken(accessToken)
                    .refreshToken(refreshToken)
                    .tokenType("Bearer")
                    .expiresIn(jwtProperties.getExpiration())
                    .refreshExpiresIn(jwtProperties.getRefreshExpiration())
                    .build();

            return Result.success(response);
        } catch (BadCredentialsException e) {
            // 记录登录失败日志
            sysLoginLogService.recordLoginLog(request.getUsername(), 0, "用户名或密码错误", httpRequest);
            throw e;
        } catch (Exception e) {
            // 记录登录失败日志
            sysLoginLogService.recordLoginLog(request.getUsername(), 0, "登录失败：" + e.getMessage(), httpRequest);
            throw e;
        }
    }

    @Operation(summary = "获取当前用户信息")
    @GetMapping("/userinfo")
    public Result<UserInfoResponse> getUserInfo() {
        String username = UserContext.getCurrentUsername();
        if (username == null) {
            return Result.failed("未登录");
        }

        SysUser user = sysUserService.getByUsername(username);
        if (user == null) {
            return Result.failed("用户不存在");
        }

        // 获取角色和权限
        List<String> roles = sysUserService.getUserRoleCodes(user.getId());
        List<String> permissions = sysUserService.getUserPermissionCodes(user.getId());

        UserInfoResponse response = UserInfoResponse.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .nickname(user.getNickname())
                .avatar(user.getAvatar())
                .deptId(user.getDeptId())
                .roles(roles)
                .permissions(permissions)
                .build();

        return Result.success(response);
    }

    @Operation(summary = "获取当前用户菜单")
    @GetMapping("/menus")
    public Result<List<MenuTreeResponse>> getUserMenus() {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            return Result.failed("未登录");
        }
        return Result.success(sysMenuService.getUserMenuTree(userId));
    }

    @Operation(summary = "退出登录")
    @PostMapping("/logout")
    public Result<Void> logout(HttpServletRequest request) {
        // 从 Authorization header 获取 Access Token
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String accessToken = authHeader.substring(7);
            try {
                // 从 Token 中获取 tokenId 并删除会话
                String tokenId = jwtTokenProvider.getTokenId(accessToken);
                if (tokenId != null) {
                    loginUserSessionService.deleteSession(tokenId);
                    log.info("用户退出登录，删除会话: tokenId={}", tokenId);
                }
            } catch (Exception e) {
                log.warn("退出登录时解析 Token 失败: {}", e.getMessage());
            }
        }

        // 删除 Refresh Token
        String refreshToken = request.getHeader("X-Refresh-Token");
        if (refreshToken != null) {
            refreshTokenService.deleteRefreshToken(refreshToken);
        }

        // 清除用户上下文
        UserContext.clear();
        return Result.success();
    }

    @Operation(summary = "刷新 Token")
    @PostMapping("/refresh")
    @RateLimiter(time = 60, count = 30, message = "Token刷新请求过于频繁，请稍后再试")
    public Result<LoginResponse> refreshToken(@Valid @RequestBody RefreshTokenRequest request) {
        String refreshToken = request.getRefreshToken();

        // 验证 Refresh Token 并获取用户名
        String username = refreshTokenService.validateAndGetUsername(refreshToken);
        if (username == null) {
            return Result.failed("刷新令牌无效或已过期");
        }

        // 删除旧的 Refresh Token
        refreshTokenService.deleteRefreshToken(refreshToken);

        // 生成新的 Access Token
        String newAccessToken = jwtTokenProvider.generateToken(username);

        // 生成新的 Refresh Token
        String newRefreshToken = refreshTokenService.generateRefreshToken(
                username,
                jwtProperties.getRefreshExpiration()
        );

        LoginResponse response = LoginResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtProperties.getExpiration())
                .refreshExpiresIn(jwtProperties.getRefreshExpiration())
                .build();

        return Result.success(response);
    }
}
