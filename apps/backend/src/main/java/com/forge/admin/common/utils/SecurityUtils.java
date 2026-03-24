package com.forge.admin.common.utils;

import com.forge.admin.modules.system.entity.SysUser;
import com.forge.admin.modules.system.service.SysUserService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * 安全工具类
 *
 * @author standadmin
 */
public class SecurityUtils {

    private static SysUserService sysUserService;

    private SecurityUtils() {
    }

    /**
     * 设置 SysUserService (用于获取完整的用户信息)
     */
    public static void setSysUserService(SysUserService service) {
        sysUserService = service;
    }

    /**
     * 获取当前认证信息
     */
    public static Authentication getAuthentication() {
        return SecurityContextHolder.getContext().getAuthentication();
    }

    /**
     * 获取当前用户
     */
    public static UserDetails getCurrentUserDetails() {
        Authentication authentication = getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof UserDetails) {
            return (UserDetails) authentication.getPrincipal();
        }
        return null;
    }

    /**
     * 获取当前用户（系统用户实体）
     * 优先从 UserContext 获取，如果不存在则从数据库加载
     */
    public static SysUser getCurrentUser() {
        // 先尝试从认证对象获取
        Authentication authentication = getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof SysUser) {
            return (SysUser) authentication.getPrincipal();
        }

        // 从 UserContext 获取用户名，然后查询完整的用户信息
        String username = UserContext.get() != null ? UserContext.get().getUsername() : null;
        if (username != null && sysUserService != null) {
            return sysUserService.getByUsername(username);
        }

        return null;
    }

    /**
     * 获取当前用户名
     */
    public static String getCurrentUsername() {
        // 优先从 UserContext 获取
        if (UserContext.get() != null) {
            return UserContext.get().getUsername();
        }

        // 从认证信息获取
        Authentication authentication = getAuthentication();
        if (authentication != null) {
            return authentication.getName();
        }
        return null;
    }

    /**
     * 判断是否已登录
     */
    public static boolean isAuthenticated() {
        Authentication authentication = getAuthentication();
        return authentication != null && authentication.isAuthenticated()
                && !"anonymousUser".equals(authentication.getPrincipal());
    }

    /**
     * 获取当前用户ID
     */
    public static Long getCurrentUserId() {
        // 优先从 UserContext 获取
        if (UserContext.get() != null) {
            return UserContext.get().getUserId();
        }

        SysUser user = getCurrentUser();
        return user != null ? user.getId() : null;
    }
}
