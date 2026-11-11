package com.forge.modules.system.auth.security;

import com.forge.common.utils.UserContext;
import com.forge.framework.tenant.core.context.TenantContextHolder;
import com.forge.modules.system.dto.user.CachedUserAuth;
import com.forge.modules.system.entity.SysUser;
import com.forge.modules.system.mapper.SysUserMapper;
import com.forge.modules.system.service.LoginUserSessionService;
import com.forge.modules.system.service.UserAuthCacheService;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * JWT 认证过滤器
 *
 * 认证路径：会话 TTL 校验（1 次 Redis）→ 用户认证上下文缓存（1 次 Redis 命中零 DB；
 * miss 时合并加载并回写，用户仅查 1 次）。权限码与数据权限均来自缓存，
 * 角色/菜单/用户变更经 UserAuthCacheService 失效钩子保证即时生效（缓存另带 5 分钟 TTL 兜底）。
 *
 * @author standadmin
 */
@Slf4j
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final SysUserMapper sysUserMapper;
    private final LoginUserSessionService loginUserSessionService;
    private final UserAuthCacheService userAuthCacheService;
    private final JwtDecoder oauth2JwtDecoder;

    public JwtAuthenticationFilter(
            JwtTokenProvider jwtTokenProvider,
            SysUserMapper sysUserMapper,
            LoginUserSessionService loginUserSessionService,
            UserAuthCacheService userAuthCacheService,
            @Lazy JwtDecoder oauth2JwtDecoder) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.sysUserMapper = sysUserMapper;
        this.loginUserSessionService = loginUserSessionService;
        this.userAuthCacheService = userAuthCacheService;
        this.oauth2JwtDecoder = oauth2JwtDecoder;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        // UserContext 为请求级 ThreadLocal：请求结束后必须清理，
        // 否则线程池复用时上一个用户的上下文会泄漏给后续未认证请求（幂等防御，与链内清理互不影响）
        try {
            try {
                authenticateRequest(request);
            } catch (Exception e) {
                logger.error("无法设置用户认证", e);
            }
            filterChain.doFilter(request, response);
        } finally {
            UserContext.clear();
        }
    }

    /**
     * 认证主体逻辑：校验会话 → 读缓存 → 设置上下文与认证信息
     */
    private void authenticateRequest(HttpServletRequest request) {
        // 从请求头获取 Token
        String token = resolveToken(request);

        // 验证 Token 并设置认证信息
        if (StringUtils.hasText(token) && jwtTokenProvider.validateToken(token)) {
            String username = jwtTokenProvider.getUsernameFromToken(token);
            String tokenId = jwtTokenProvider.getTokenId(token);
            Long tenantId = jwtTokenProvider.getTenantId(token);

            // 检查会话是否有效（防止强制下线后 Token 仍然可用）
            Long sessionTTL = loginUserSessionService.getSessionTTL(tokenId);
            if (sessionTTL == null || sessionTTL <= 0) {
                log.warn("[JWT认证] 会话已失效或被强制下线: username={}, tokenId={}", username, tokenId);
                // 会话无效，不设置认证信息，请求将被当作未认证处理
                // 直接返回：authenticateRequest 正常返回后 doFilterInternal 统一放行
                return;
            }

            // 加载阶段：临时把 TenantContextHolder 切到 JWT.tenantId
            // 平台超管切换租户场景：header.tenantId=目标租户，但 JWT.tenantId=admin 所属租户
            // 查用户/角色必须按 JWT.tenantId，否则会查到目标租户的同名用户或越权
            // 加载完成后恢复 header 中的 tenantId，让后续业务 SQL 按目标租户过滤
            Long prevTenantId = TenantContextHolder.getTenantId();
            try {
                if (tenantId != null) {
                    TenantContextHolder.setTenantId(tenantId);
                }

                // 读认证上下文（缓存命中零 DB；miss 合并加载并回写）
                CachedUserAuth auth = userAuthCacheService.getAuth(tenantId, username);
                if (auth != null) {
                    // 设置用户上下文
                    setUserContext(auth);

                    // 创建认证对象并设置到 SecurityContext
                    UserDetails userDetails = buildUserDetails(auth);
                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            } finally {
                // 恢复 TenantContextHolder 为 header 值，让后续业务 SQL 按 header 过滤
                if (prevTenantId != null) {
                    TenantContextHolder.setTenantId(prevTenantId);
                }
            }
        } else if (StringUtils.hasText(token)) {
            // 系统 JWT 验证失败，尝试作为 OAuth2 token 处理
            handleOAuth2Token(token);
        }
    }

    /**
     * 处理 OAuth2 token：尝试用 OAuth2 JWT 解码器验证 RS256 token，
     * 验证成功后根据 sub（client_id）映射系统用户并设置完整权限。
     * 服务账号为低频机流量且无租户概念（tenantId=null），不走缓存直接合并加载。
     */
    private boolean handleOAuth2Token(String token) {
        try {
            var jwt = oauth2JwtDecoder.decode(token);
            String clientId = jwt.getClaimAsString("sub");
            if (clientId == null) {
                return false;
            }

            // 尝试用 client_id 作为用户名查找系统用户（服务账号）
            // 服务账号无租户概念，传入 null 按 username 查找
            SysUser user = sysUserMapper.selectByUsernameSimple(null, clientId);
            if (user == null) {
                log.warn("[OAuth2认证] 未找到服务账号用户: clientId={}", clientId);
                return false;
            }
            if (user.getStatus() == null || user.getStatus() != 1) {
                log.warn("[OAuth2认证] 服务账号已禁用: clientId={}", clientId);
                return false;
            }

            CachedUserAuth auth = userAuthCacheService.loadFresh(user);

            // 设置用户上下文
            setUserContext(auth);

            UserDetails userDetails = buildUserDetails(auth);
            UsernamePasswordAuthenticationToken systemAuth =
                    new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
            SecurityContextHolder.getContext().setAuthentication(systemAuth);

            log.debug("[OAuth2认证] 服务账号映射成功: clientId={}, authorities={}", clientId, userDetails.getAuthorities().size());
            return true;
        } catch (Exception e) {
            // 不是有效的 OAuth2 JWT token，忽略
            return false;
        }
    }

    /**
     * 从请求头或 Cookie 获取 Token
     */
    private String resolveToken(HttpServletRequest request) {
        // 优先从 Authorization 请求头获取
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }

        // 从 Cookie 中获取（支持 OAuth2 授权码流程的浏览器重定向）
        if (request.getCookies() != null) {
            for (jakarta.servlet.http.Cookie cookie : request.getCookies()) {
                if ("access_token".equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }

        return null;
    }

    /**
     * 设置用户上下文（数据来源：认证缓存载体）
     */
    private void setUserContext(CachedUserAuth auth) {
        UserContext context = new UserContext();
        context.setUserId(auth.getUserId());
        context.setUsername(auth.getUsername());
        context.setNickname(auth.getNickname());
        context.setDeptId(auth.getDeptId());
        // tenantId 已是 user.tenantId（缓存加载时以 DB 为准）
        context.setTenantId(auth.getTenantId());
        context.setAccountType(auth.getAccountType());
        context.setRoles(auth.getRoles());
        context.setMaxDataScope(auth.getMaxDataScope());

        UserContext.set(context);

        log.debug("[JWT认证] 用户上下文设置完成 - username: {}, tenantId: {}, maxDataScope: {}, roles: {}",
                auth.getUsername(), auth.getTenantId(), auth.getMaxDataScope(),
                auth.getRoles() != null ? auth.getRoles().size() : 0);
    }

    /**
     * 从缓存载体组装 UserDetails（filter 路径不做密码校验，password 置 null；
     * 登录认证走 AuthenticationManager → UserDetailsService 直查 DB，不受影响）
     */
    private UserDetails buildUserDetails(CachedUserAuth auth) {
        List<SimpleGrantedAuthority> authorities = auth.getPermissionCodes() == null
                ? List.of()
                : auth.getPermissionCodes().stream().map(SimpleGrantedAuthority::new).toList();
        return User.builder()
                .username(auth.getUsername())
                .password(null)
                .authorities(authorities)
                .accountExpired(false)
                .accountLocked(false)
                .credentialsExpired(false)
                .disabled(false)
                .build();
    }
}
