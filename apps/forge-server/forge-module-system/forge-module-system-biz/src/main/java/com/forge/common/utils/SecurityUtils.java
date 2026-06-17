package com.forge.common.utils;

import com.forge.framework.security.utils.SecurityHelper;
import com.forge.modules.system.entity.SysUser;
import com.forge.modules.system.service.SysUserService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * 安全工具类（扩展版）
 *
 * 扩展SecurityHelper，提供依赖SysUser的额外功能
 *
 * @author standadmin
 */
public class SecurityUtils extends SecurityHelper {

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
     * 获取当前用户（系统用户实体）
     * 优先从 UserContext 获取，如果不存在则从数据库加载
     */
    public static SysUser getCurrentUser() {
        // 先尝试从认证对象获取
        if (getAuthentication() != null && getAuthentication().getPrincipal() instanceof SysUser) {
            return (SysUser) getAuthentication().getPrincipal();
        }

        // 从 UserContext 获取用户名，然后查询完整的用户信息
        String username = getCurrentUsername();
        if (username != null && sysUserService != null) {
            return sysUserService.getByUsername(username);
        }

        return null;
    }

    /**
     * 获取当前用户昵称
     * 优先从UserContext获取，其次从SysUser获取
     */
    public static String getCurrentNickname() {
        // 先调用父类方法
        String nickname = SecurityHelper.getCurrentNickname();
        if (nickname != null) {
            return nickname;
        }

        // 从SysUser获取
        SysUser user = getCurrentUser();
        return user != null ? user.getNickname() : null;
    }
}
