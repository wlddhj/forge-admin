package com.forge.admin.common.utils;

import com.forge.admin.common.enumeration.DataScope;
import com.forge.admin.modules.system.entity.SysRole;
import com.forge.admin.modules.system.entity.SysUser;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 数据权限工具类
 *
 * @author standadmin
 */
@Slf4j
public class DataScopeUtils {

    /**
     * 构建数据权限 SQL 过滤条件
     *
     * @param user 当前用户
     * @param userAlias 用户表别名
     * @param deptAlias 部门表别名
     * @return SQL 过滤条件
     */
    public static String buildDataScopeFilter(SysUser user, String userAlias, String deptAlias) {
        if (user == null) {
            return "1=0";
        }

        // 超级管理员拥有所有数据权限
        if (user.getAccountType() != null && user.getAccountType() == 1) {
            return null;
        }

        // 获取用户的最大数据权限范围
        DataScope dataScope = getMaxDataScope(user);
        if (dataScope == null) {
            return "1=0";
        }

        Long deptId = user.getDeptId();

        switch (dataScope) {
            case ALL:
                // 全部数据权限
                return null;

            case CUSTOM:
                // 自定义数据权限（需要从角色配置的部门中获取）
                return buildCustomDataScope(user, deptAlias);

            case DEPT:
                // 本部门数据权限
                if (deptId != null) {
                    return String.format("%s.dept_id = %d", userAlias, deptId);
                }
                return "1=0";

            case DEPT_AND_CHILD:
                // 本部门及以下数据权限
                if (deptId != null) {
                    List<Long> deptIds = DeptDataScopeUtils.getChildDeptIds(deptId);
                    if (deptIds.isEmpty()) {
                        return String.format("%s.dept_id = %d", userAlias, deptId);
                    }
                    String deptIdsStr = deptIds.stream()
                            .map(String::valueOf)
                            .collect(Collectors.joining(","));
                    return String.format("%s.dept_id IN (%s)", userAlias, deptIdsStr);
                }
                return "1=0";

            case SELF:
                // 仅本人数据权限
                return String.format("%s.id = %d", userAlias, user.getId());

            default:
                return "1=0";
        }
    }

    /**
     * 获取用户的最大数据权限范围
     *
     * @param user 用户
     * @return 数据权限范围
     */
    public static DataScope getMaxDataScope(SysUser user) {
        if (user.getRoles() == null || user.getRoles().isEmpty()) {
            return DataScope.SELF;
        }

        // 按权限范围从小到大排序
        List<DataScope> scopes = user.getRoles().stream()
                .map(SysRole::getDataScope)
                .filter(Objects::nonNull)
                .map(dataScopeStr -> {
                    try {
                        return DataScope.fromValue(dataScopeStr);
                    } catch (IllegalArgumentException e) {
                        log.warn("无效的数据权限值: {}", dataScopeStr);
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .distinct()
                .sorted(Comparator.comparing(DataScope::getValue))
                .collect(Collectors.toList());

        if (scopes.isEmpty()) {
            return DataScope.SELF;
        }

        // 返回最大的权限范围（最小的数值）
        return scopes.get(0);
    }

    /**
     * 构建自定义数据权限 SQL
     *
     * @param user 用户
     * @param deptAlias 部门表别名
     * @return SQL 条件
     */
    private static String buildCustomDataScope(SysUser user, String deptAlias) {
        // 获取用户所有角色中的自定义部门ID列表
        List<Long> deptIds = new ArrayList<>();
        if (user.getRoles() != null) {
            for (var role : user.getRoles()) {
                // 只处理自定义数据权限的角色
                if ("2".equals(role.getDataScope()) && role.getDeptIds() != null) {
                    deptIds.addAll(role.getDeptIds());
                }
            }
        }

        // 去重
        deptIds = deptIds.stream().distinct().toList();

        if (deptIds.isEmpty()) {
            // 如果没有配置任何自定义部门，返回本部门数据权限
            Long deptId = user.getDeptId();
            if (deptId != null) {
                return String.format("%s.dept_id = %d", deptAlias, deptId);
            }
            return "1=0";
        }

        // 构建自定义部门权限 SQL
        if (deptIds.size() == 1) {
            return String.format("%s.dept_id = %d", deptAlias, deptIds.get(0));
        } else {
            String deptIdsStr = deptIds.stream()
                    .map(String::valueOf)
                    .collect(Collectors.joining(", "));
            return String.format("%s.dept_id IN (%s)", deptAlias, deptIdsStr);
        }
    }

    /**
     * 检查用户是否有指定部门的数据权限
     *
     * @param user 用户
     * @param deptId 部门ID
     * @return 是否有权限
     */
    public static boolean hasDataScopePermission(SysUser user, Long deptId) {
        if (user == null || deptId == null) {
            return false;
        }

        // 超级管理员拥有所有权限
        if (user.getAccountType() != null && user.getAccountType() == 1) {
            return true;
        }

        DataScope dataScope = getMaxDataScope(user);

        switch (dataScope) {
            case ALL:
                return true;
            case CUSTOM:
            case DEPT:
                return deptId.equals(user.getDeptId());
            case DEPT_AND_CHILD:
                List<Long> deptIds = DeptDataScopeUtils.getChildDeptIds(user.getDeptId());
                return deptIds.contains(deptId);
            case SELF:
                return false;
            default:
                return false;
        }
    }
}
