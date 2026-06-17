package com.forge.framework.security.utils;

import com.forge.common.utils.UserContext;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * 安全工具类
 *
 * 提供基于Spring Security的安全操作，所有模块都可以使用
 *
 * @author standadmin
 */
public class SecurityHelper {

    protected SecurityHelper() {
    }

    /**
     * 获取当前认证信息
     */
    public static Authentication getAuthentication() {
        return SecurityContextHolder.getContext().getAuthentication();
    }

    /**
     * 获取当前用户详情
     */
    public static UserDetails getCurrentUserDetails() {
        Authentication authentication = getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof UserDetails) {
            return (UserDetails) authentication.getPrincipal();
        }
        return null;
    }

    /**
     * 获取当前用户名
     *
     * 优先从UserContext获取，如果不存在则从认证信息获取
     */
    public static String getCurrentUsername() {
        // 优先从UserContext获取
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
     * 获取当前用户ID
     *
     * 优先从UserContext获取，如果不存在则尝试从认证信息解析
     */
    public static Long getCurrentUserId() {
        // 优先从UserContext获取
        if (UserContext.get() != null) {
            return UserContext.get().getUserId();
        }

        // 从认证信息获取（需要认证对象中包含用户ID信息）
        Authentication authentication = getAuthentication();
        if (authentication != null) {
            Object principal = authentication.getPrincipal();

            // 如果principal是UserDetails，尝试解析用户名作为ID
            if (principal instanceof UserDetails) {
                UserDetails userDetails = (UserDetails) principal;
                try {
                    return Long.parseLong(userDetails.getUsername());
                } catch (NumberFormatException e) {
                    // 用户名不是数字，无法解析为ID
                    return null;
                }
            }

            // 如果principal是String，尝试解析
            if (principal instanceof String) {
                try {
                    return Long.parseLong((String) principal);
                } catch (NumberFormatException e) {
                    return null;
                }
            }
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
     * 判断是否为管理员
     *
     * 从UserContext获取账户类型判断
     */
    public static boolean isAdmin() {
        UserContext context = UserContext.get();
        return context != null && context.isAdmin();
    }

    /**
     * 获取当前用户部门ID
     */
    public static Long getCurrentDeptId() {
        UserContext context = UserContext.get();
        return context != null ? context.getDeptId() : null;
    }

    /**
     * 获取当前用户昵称
     */
    public static String getCurrentNickname() {
        UserContext context = UserContext.get();
        return context != null ? context.getNickname() : getCurrentUsername();
    }
}