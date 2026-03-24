package com.forge.admin.modules.auth.security;

import com.forge.admin.common.config.JwtProperties;
import com.forge.admin.common.enumeration.DataScope;
import com.forge.admin.common.utils.UserContext;
import com.forge.admin.modules.system.entity.SysRole;
import com.forge.admin.modules.system.entity.SysUser;
import com.forge.admin.modules.system.mapper.SysRoleDeptMapper;
import com.forge.admin.modules.system.mapper.SysUserMapper;
import com.forge.admin.modules.system.service.SysRoleService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * JWT 认证过滤器
 *
 * 借鉴 shi9-boot 设计，在认证时加载完整的数据权限信息到 UserContext
 *
 * @author standadmin
 */
@Slf4j
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final UserDetailsService userDetailsService;
    private final SysUserMapper sysUserMapper;
    private final SysRoleService sysRoleService;
    private final SysRoleDeptMapper sysRoleDeptMapper;

    public JwtAuthenticationFilter(
            JwtTokenProvider jwtTokenProvider,
            UserDetailsService userDetailsService,
            SysUserMapper sysUserMapper,
            @Lazy SysRoleService sysRoleService,
            SysRoleDeptMapper sysRoleDeptMapper) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.userDetailsService = userDetailsService;
        this.sysUserMapper = sysUserMapper;
        this.sysRoleService = sysRoleService;
        this.sysRoleDeptMapper = sysRoleDeptMapper;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        try {
            // 从请求头获取 Token
            String token = resolveToken(request);

            // 验证 Token 并设置认证信息
            if (StringUtils.hasText(token) && jwtTokenProvider.validateToken(token)) {
                String username = jwtTokenProvider.getUsernameFromToken(token);

                // 加载用户信息
                UserDetails userDetails = userDetailsService.loadUserByUsername(username);

                // 设置用户上下文
                setUserContext(username);

                // 创建认证对象
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                // 设置到 SecurityContext
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        } catch (Exception e) {
            logger.error("无法设置用户认证", e);
        }

        filterChain.doFilter(request, response);
    }

    /**
     * 从请求头获取 Token
     */
    private String resolveToken(HttpServletRequest request) {
        String bearerToken = request.getHeader(JwtProperties.class.getDeclaredAnnotations()
                .length > 0 ? "Authorization" : "Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }

    /**
     * 设置用户上下文
     *
     * 借鉴 shi9-boot 设计，加载完整的数据权限信息
     */
    private void setUserContext(String username) {
        // 1. 查询用户基本信息（使用直接查询避免拦截器）
        SysUser user = sysUserMapper.selectByUsernameSimple(username);
        if (user == null) {
            log.warn("[JWT认证] 用户不存在: {}", username);
            return;
        }

        // 2. 加载角色和数据权限信息
        List<SysRole> roles = loadUserRolesWithDataScope(user);

        // 3. 计算最大数据权限范围
        DataScope maxDataScope = calculateMaxDataScope(roles);

        // 4. 构建 UserContext
        UserContext context = new UserContext();
        context.setUserId(user.getId());
        context.setUsername(user.getUsername());
        context.setNickname(user.getNickname());
        context.setDeptId(user.getDeptId());
        // context.setDeptName(user.getDeptName()); // SysUser 没有 deptName 字段，如需要可通过 DeptService 查询
        context.setAccountType(user.getAccountType());

        // 转换角色信息
        List<UserContext.DataScopeRoleInfo> roleInfos = roles.stream()
            .map(r -> {
                UserContext.DataScopeRoleInfo info = new UserContext.DataScopeRoleInfo();
                info.setRoleId(r.getId());
                info.setRoleCode(r.getRoleCode());
                info.setDataScope(DataScope.fromCode(r.getDataScope()));

                // 如果是自定义权限，加载部门列表
                if (DataScope.CUSTOM.equals(info.getDataScope())) {
                    List<Long> deptIds = sysRoleDeptMapper.selectDeptIdsByRoleId(r.getId());
                    info.setDeptIds(deptIds);
                }
                return info;
            })
            .collect(Collectors.toList());

        context.setRoles(roleInfos);
        context.setMaxDataScope(maxDataScope);

        // 5. 设置到 ThreadLocal
        UserContext.set(context);

        log.debug("[JWT认证] 用户上下文设置完成 - username: {}, maxDataScope: {}, roles: {}",
            username, maxDataScope, roleInfos.size());
    }

    /**
     * 计算最大数据权限范围
     */
    private DataScope calculateMaxDataScope(List<SysRole> roles) {
        if (roles == null || roles.isEmpty()) {
            return DataScope.SELF;
        }

        return roles.stream()
            .map(r -> {
                try {
                    return DataScope.fromCode(r.getDataScope());
                } catch (Exception e) {
                    log.warn("[数据权限] 无效的数据权限值: {}", r.getDataScope());
                    return DataScope.SELF;
                }
            })
            .max(Comparator.comparing(DataScope::getLevel))
            .orElse(DataScope.SELF);
    }

    /**
     * 加载用户角色和数据权限信息
     */
    private List<SysRole> loadUserRolesWithDataScope(SysUser user) {
        List<Long> roleIds = sysUserMapper.selectRoleIdsByUserId(user.getId());
        if (roleIds == null || roleIds.isEmpty()) {
            return Collections.emptyList();
        }
        return sysRoleService.listByIds(roleIds);
    }
}
