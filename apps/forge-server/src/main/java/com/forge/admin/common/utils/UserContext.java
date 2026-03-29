package com.forge.admin.common.utils;

import com.forge.admin.common.enumeration.DataScope;
import lombok.Data;

import java.util.List;
import java.util.Map;
import java.util.HashMap;

/**
 * 用户上下文信息
 *
 * 借鉴 shi9-boot 的 LoginUser 设计，支持数据权限缓存
 *
 * @author standadmin
 */
@Data
public class UserContext {

    private Long userId;
    private String username;
    private String nickname;
    private Long deptId;
    private String deptName;

    /**
     * 账户类型（0:普通 1:管理员）
     */
    private Integer accountType;

    /**
     * 最大数据权限范围
     */
    private DataScope maxDataScope;

    /**
     * 角色数据权限信息列表
     */
    private List<DataScopeRoleInfo> roles;

    /**
     * 上下文缓存（避免重复计算数据权限）
     * 借鉴 shi9-boot 的 context 设计
     */
    private Map<String, Object> contextCache;

    private static final ThreadLocal<UserContext> CONTEXT = new ThreadLocal<>();

    public static void set(UserContext context) {
        CONTEXT.set(context);
    }

    public static UserContext get() {
        return CONTEXT.get();
    }

    public static void clear() {
        CONTEXT.remove();
    }

    public static Long getCurrentUserId() {
        UserContext context = get();
        return context != null ? context.getUserId() : null;
    }

    public static String getCurrentUsername() {
        UserContext context = get();
        return context != null ? context.getUsername() : null;
    }

    /**
     * 判断是否为超级管理员
     */
    public boolean isAdmin() {
        return accountType != null && accountType == 1;
    }

    /**
     * 判断是否拥有全部数据权限
     */
    public boolean hasAllDataScope() {
        return isAdmin() || maxDataScope == DataScope.ALL;
    }

    /**
     * 从上下文缓存获取数据（借鉴 shi9-boot）
     *
     * @param key 缓存键
     * @param type 目标类型
     * @return 缓存的值，不存在返回 null
     */
    public <T> T getContext(String key, Class<T> type) {
        if (contextCache == null) {
            return null;
        }
        Object value = contextCache.get(key);
        return value != null ? type.cast(value) : null;
    }

    /**
     * 设置上下文缓存
     *
     * @param key 缓存键
     * @param value 缓存值
     */
    public void setContext(String key, Object value) {
        if (contextCache == null) {
            contextCache = new HashMap<>();
        }
        contextCache.put(key, value);
    }

    /**
     * 角色数据权限信息
     */
    @Data
    public static class DataScopeRoleInfo {
        private Long roleId;
        private String roleCode;
        private DataScope dataScope;
        private List<Long> deptIds;
    }
}
