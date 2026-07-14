package com.forge.modules.system.controller.admin;

import com.forge.framework.web.annotation.RateLimiter;
import com.forge.framework.security.config.JwtProperties;
import com.forge.framework.tenant.core.context.TenantContextHolder;
import com.forge.common.exception.BusinessException;
import com.forge.common.response.Result;
import com.forge.common.response.ResultCode;
import com.forge.common.utils.IpUtils;
import com.forge.common.utils.UserContext;
import com.forge.modules.system.auth.dto.LoginRequest;
import com.forge.modules.system.auth.dto.LoginResponse;
import com.forge.modules.system.auth.dto.RefreshTokenRequest;
import com.forge.modules.system.auth.dto.UserInfoResponse;
import com.forge.modules.system.auth.security.JwtTokenProvider;
import com.forge.modules.system.auth.service.CaptchaService;
import com.forge.modules.system.auth.service.LoginAttemptService;
import com.forge.modules.system.auth.service.RefreshTokenService;
import com.forge.modules.system.auth.properties.LoginPolicyProperties;
import com.forge.modules.system.auth.properties.PasswordPolicyProperties;
import com.forge.modules.system.dto.menu.MenuTreeResponse;
import com.forge.modules.system.dto.online.LoginUserSession;
import com.forge.modules.system.entity.SysTenant;
import com.forge.modules.system.entity.SysUser;
import com.forge.modules.system.service.LoginUserSessionService;
import com.forge.modules.system.service.SysLoginLogService;
import com.forge.modules.system.service.SysMenuService;
import com.forge.modules.system.service.SysTenantService;
import com.forge.modules.system.service.SysUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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
    private final CaptchaService captchaService;
    private final LoginAttemptService loginAttemptService;
    private final LoginPolicyProperties loginPolicyProperties;
    private final PasswordPolicyProperties passwordPolicyProperties;
    private final SysTenantService sysTenantService;
    private final com.forge.framework.tenant.config.TenantProperties tenantProperties;

        @Operation(summary = "登录")
    @PostMapping("/login")
    @RateLimiter(keyType = RateLimiter.KeyType.USERNAME, time = 60, count = 20, message = "登录请求过于频繁，请稍后再试")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        // 在 try 之前提取客户端信息，确保 catch 中也能访问
        String loginIp = com.forge.common.utils.IpUtils.getClientIp(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");
        String username = request.getUsername();

        // 1. 检查账户锁定状态
        if (loginAttemptService.isLocked(username)) {
            long remainingSeconds = loginAttemptService.getRemainingLockSeconds(username);
            sysLoginLogService.recordLoginLog(username, 0, "账户已锁定，剩余" + remainingSeconds + "秒", loginIp, userAgent);
            return Result.failed("账户已锁定，请" + (remainingSeconds / 60 + 1) + "分钟后重试");
        }

        // 2. 校验验证码
        if (!captchaService.validate(request.getCaptchaId(), request.getCaptchaCode())) {
            sysLoginLogService.recordLoginLog(username, 0, "验证码错误", loginIp, userAgent);
            return Result.failed("验证码错误或已过期");
        }

        // 2.5 解析租户：启用多租户时按 tenantCode 查；关闭时使用默认租户(id=1, code='default')
        Long tenantId;
        SysTenant tenant;
        if (Boolean.FALSE.equals(tenantProperties.getEnable())) {
            // 单租户模式：跳过 tenantCode 校验，使用默认租户
            tenantId = 1L;
            tenant = sysTenantService.getById(tenantId);
            if (tenant == null) {
                // 兜底：若默认租户不存在（迁移未执行），允许继续但 tenant 字段为 null
                log.warn("[登录] 多租户已关闭且默认租户不存在，按无租户上下文处理");
                tenantId = null;
            }
        } else {
            // 多租户模式：tenantCode 必填
            if (request.getTenantCode() == null || request.getTenantCode().isBlank()) {
                sysLoginLogService.recordLoginLog(username, 0, "租户标识未传递", loginIp, userAgent);
                throw new BusinessException(ResultCode.VALIDATE_FAILED, "租户标识不能为空");
            }
            tenantId = sysTenantService.getIdByCode(request.getTenantCode());
            if (tenantId == null) {
                sysLoginLogService.recordLoginLog(username, 0, "租户不存在", loginIp, userAgent);
                throw new BusinessException(ResultCode.TENANT_NOT_EXISTS);
            }
            tenant = sysTenantService.getById(tenantId);
            if (tenant == null) {
                sysLoginLogService.recordLoginLog(username, 0, "租户不存在", loginIp, userAgent);
                throw new BusinessException(ResultCode.TENANT_NOT_EXISTS);
            }
            if (tenant.getStatus() != null && tenant.getStatus() != 1) {
                sysLoginLogService.recordLoginLog(username, 0, "租户已被禁用", loginIp, userAgent);
                throw new BusinessException(ResultCode.TENANT_DISABLED);
            }
            if (tenant.getExpireTime() != null && tenant.getExpireTime().isBefore(java.time.LocalDateTime.now())) {
                sysLoginLogService.recordLoginLog(username, 0, "租户已过期", loginIp, userAgent);
                throw new BusinessException(ResultCode.TENANT_EXPIRED);
            }
        }
        // 单租户模式下 tenantId 可能为 null（默认租户不存在时），不设置 TenantContextHolder
        if (tenantId != null) {
            TenantContextHolder.setTenantId(tenantId);
        }

        try {
            // 认证
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(username, request.getPassword())
            );

            // 获取用户信息（按 tenantId + username 联合过滤）
            SysUser user = sysUserService.getByUsername(tenantId, username);
            if (user == null) {
                loginAttemptService.recordFailure(username);
                return Result.failed("用户不存在");
            }

            // 3. 检查首次登录强制改密
            if (user.getFirstLogin() != null && user.getFirstLogin() == 1) {
                // 返回特殊响应，提示前端跳转改密页
                LoginResponse response = LoginResponse.builder()
                        .needChangePassword(true)
                        .message("首次登录请修改密码")
                        .build();
                return Result.success(response);
            }

            // 生成 tokenId
            String tokenId = java.util.UUID.randomUUID().toString().replace("-", "");

            // 生成 Access Token（使用相同的 tokenId 关联会话，写入 tenantId claim）
            String accessToken = jwtTokenProvider.generateTokenWithId(username, tokenId, tenantId);

            // 生成 Refresh Token
            String refreshToken = refreshTokenService.generateRefreshToken(
                    username,
                    jwtProperties.getRefreshExpiration()
            );

            // 解析客户端信息
            String browser = com.forge.common.utils.IpUtils.getBrowser(userAgent);
            String os = com.forge.common.utils.IpUtils.getOs(userAgent);
            String loginLocation = com.forge.common.utils.IpUtils.getLocationByIp(loginIp);

            // 保存登录会话到 Redis
            long currentTime = System.currentTimeMillis();
            LoginUserSession session = LoginUserSession.builder()
                    .tokenId(tokenId)
                    .userId(user.getId())
                    .username(user.getUsername())
                    .nickname(user.getNickname())
                    .loginIp(loginIp)
                    .loginLocation(loginLocation)
                    .browser(browser)
                    .os(os)
                    .loginTime(currentTime)
                    .lastActiveTime(currentTime)
                    .refreshToken(refreshToken)
                    .build();
            loginUserSessionService.saveSession(session, jwtProperties.getRefreshExpiration());

            // 单点登录 / 并发会话控制
            if (loginPolicyProperties.isSingleSession()) {
                // 单点登录模式：踢掉该用户的其他所有会话
                int kicked = loginUserSessionService.kickOutUserSessions(username, tokenId);
                if (kicked > 0) {
                    log.info("单点登录: 用户 {} 新登录踢出 {} 个旧会话", username, kicked);
                }
            } else {
                // 并发模式：超过 maxConcurrentSessions 时踢出最早的会话
                List<LoginUserSession> userSessions = loginUserSessionService.getSessionsByUsername(username);
                int maxConcurrent = loginPolicyProperties.getMaxConcurrentSessions();
                while (userSessions.size() > maxConcurrent) {
                    // 列表按 loginTime 倒序，最后一个是最早的
                    LoginUserSession oldest = userSessions.remove(userSessions.size() - 1);
                    if (oldest.getTokenId().equals(tokenId)) {
                        continue; // 不踢自己
                    }
                    loginUserSessionService.deleteSession(oldest.getTokenId());
                    log.info("并发会话超限: 用户 {} 踢出最早会话 tokenId={}", username, oldest.getTokenId());
                }
            }

            // 4. 记录登录成功，清除失败计数
            loginAttemptService.recordSuccess(username);

            // 记录登录成功日志
            sysLoginLogService.recordLoginLog(username, 1, "登录成功", loginIp, userAgent);

            // 5. 密码过期检查
            Integer passwordExpireDays = null;
            boolean passwordExpired = false;
            if (user.getPasswordUpdateTime() != null) {
                long elapsedDays = java.time.Duration.between(
                        user.getPasswordUpdateTime(),
                        java.time.LocalDateTime.now()
                ).toDays();
                int expireDays = passwordPolicyProperties.getExpireDays();
                int remaining = (int) (expireDays - elapsedDays);
                if (remaining <= 0) {
                    // 已过期：标记需强制改密
                    passwordExpired = true;
                    user.setFirstLogin(1);
                    sysUserService.updateById(user);
                } else {
                    passwordExpireDays = remaining;
                }
            }

            LoginResponse response = LoginResponse.builder()
                    .accessToken(accessToken)
                    .refreshToken(refreshToken)
                    .tokenType("Bearer")
                    .expiresIn(jwtProperties.getExpiration())
                    .refreshExpiresIn(jwtProperties.getRefreshExpiration())
                    .tenantId(tenantId)
                    .tenantCode(request.getTenantCode())
                    .tenantName(tenant != null ? tenant.getName() : null)
                    .passwordExpireDays(passwordExpireDays)
                    .passwordExpired(passwordExpired)
                    .build();

            // 设置 access_token Cookie（支持 OAuth2 授权码流程的浏览器重定向）
            Cookie tokenCookie = new Cookie("access_token", accessToken);
            tokenCookie.setHttpOnly(true);
            tokenCookie.setPath("/");
            tokenCookie.setMaxAge(jwtProperties.getExpiration().intValue());
            httpResponse.addCookie(tokenCookie);

            return Result.success(response);
        } catch (BadCredentialsException e) {
            // 5. 记录登录失败，增加失败计数
            loginAttemptService.recordFailure(username);
            // 记录登录失败日志
            sysLoginLogService.recordLoginLog(username, 0, "用户名或密码错误", loginIp, userAgent);
            // 返回业务错误（HTTP 200 + code 5102），避免前端误判为 token 过期触发刷新流程
            return Result.failed(5102, "用户名或密码错误");
        } catch (Exception e) {
            // 记录登录失败日志
            sysLoginLogService.recordLoginLog(username, 0, "登录失败：" + e.getMessage(), loginIp, userAgent);
            return Result.failed("登录失败：" + e.getMessage());
        }
    }

    @Operation(summary = "获取验证码")
    @GetMapping("/captcha")
    public Result<com.forge.modules.system.auth.dto.CaptchaResponse> getCaptcha() {
        return Result.success(captchaService.generate());
    }

    @Operation(summary = "获取当前用户信息")
    @GetMapping("/userinfo")
    public Result<UserInfoResponse> getUserInfo() {
        String username = UserContext.getCurrentUsername();
        if (username == null) {
            return Result.failed("未登录");
        }

        // 平台超管切换租户时，UserContext.tenantId(JWT) 与 TenantContextHolder.tenantId(header) 可能不同
        // 用 setIgnore 跳过 MP 自动注入，selectByUsernameSimple 自带 tenant_id 条件按 UserContext.tenantId 查
        Long userTenantId = UserContext.get().getTenantId();
        boolean prevIgnore = TenantContextHolder.isIgnore();
        TenantContextHolder.setIgnore(true);
        SysUser user;
        try {
            user = sysUserService.getByUsername(userTenantId, username);
        } finally {
            TenantContextHolder.setIgnore(prevIgnore);
        }
        if (user == null) {
            return Result.failed("用户不存在");
        }

        // 获取角色和权限（同样需要 setIgnore，因为这些查询按 user.tenantId 而非 header.tenantId）
        List<String> roles;
        List<String> permissions;
        try {
            TenantContextHolder.setIgnore(true);
            roles = sysUserService.getUserRoleCodes(user.getId());
            permissions = sysUserService.getUserPermissionCodes(user.getId());
        } finally {
            TenantContextHolder.setIgnore(prevIgnore);
        }

        // 密码过期检查
        Integer passwordExpireDays = null;
        boolean passwordExpired = false;
        if (user.getPasswordUpdateTime() != null) {
            long elapsedDays = java.time.Duration.between(
                    user.getPasswordUpdateTime(),
                    java.time.LocalDateTime.now()
            ).toDays();
            int expireDays = passwordPolicyProperties.getExpireDays();
            int remaining = (int) (expireDays - elapsedDays);
            if (remaining <= 0) {
                passwordExpired = true;
            } else {
                passwordExpireDays = remaining;
            }
        }

        UserInfoResponse response = UserInfoResponse.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .nickname(user.getNickname())
                .avatar(user.getAvatar())
                .deptId(user.getDeptId())
                .roles(roles)
                .permissions(permissions)
                .passwordExpireDays(passwordExpireDays)
                .passwordExpired(passwordExpired)
                .tenantId(user.getTenantId())
                .accountType(user.getAccountType())
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
    public Result<Void> logout(HttpServletRequest request, HttpServletResponse httpResponse) {
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
        TenantContextHolder.clear();

        // 清除 access_token Cookie
        Cookie tokenCookie = new Cookie("access_token", null);
        tokenCookie.setHttpOnly(true);
        tokenCookie.setPath("/");
        tokenCookie.setMaxAge(0);
        httpResponse.addCookie(tokenCookie);

        return Result.success();
    }

    @Operation(summary = "心跳接口")
    @PostMapping("/heartbeat")
    public Result<Void> heartbeat(HttpServletRequest request) {
        // 从 Authorization header 获取 Access Token
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String accessToken = authHeader.substring(7);
            try {
                String tokenId = jwtTokenProvider.getTokenId(accessToken);
                if (tokenId != null) {
                    loginUserSessionService.updateLastActiveTime(tokenId);
                }
            } catch (Exception e) {
                log.warn("心跳更新失败: {}", e.getMessage());
            }
        }
        return Result.success();
    }

    @Operation(summary = "刷新 Token")
    @PostMapping("/refresh")
    @RateLimiter(time = 60, count = 30, message = "Token刷新请求过于频繁，请稍后再试")
    public Result<LoginResponse> refreshToken(@Valid @RequestBody RefreshTokenRequest request,
                                               HttpServletRequest httpRequest) {
        String refreshToken = request.getRefreshToken();

        // 验证 Refresh Token 并获取用户名
        String username = refreshTokenService.validateAndGetUsername(refreshToken);
        if (username == null) {
            return Result.failed("刷新令牌无效或已过期");
        }

        // 删除旧的 Refresh Token
        refreshTokenService.deleteRefreshToken(refreshToken);

        // 生成新的 tokenId
        String tokenId = java.util.UUID.randomUUID().toString().replace("-", "");

        // 保存新会话到 Redis
        SysUser user = sysUserService.getByUsername(UserContext.get().getTenantId(), username);

        // 生成新的 Access Token（使用 tokenId 关联会话，写入 tenantId claim）
        Long tenantId = user != null ? user.getTenantId() : null;
        String newAccessToken = jwtTokenProvider.generateTokenWithId(username, tokenId, tenantId);

        // 生成新的 Refresh Token
        String newRefreshToken = refreshTokenService.generateRefreshToken(
                username,
                jwtProperties.getRefreshExpiration()
        );

        if (user != null) {
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
        }

        LoginResponse response = LoginResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtProperties.getExpiration())
                .refreshExpiresIn(jwtProperties.getRefreshExpiration())
                .tenantId(tenantId)
                .build();

        return Result.success(response);
    }
}
