package com.forge.admin.common.permission;

import lombok.Data;

import java.util.HashSet;
import java.util.Set;

/**
 * 数据权限上下文
 *
 * 存储数据权限规则过滤信息
 * 基于 shi9-boot 设计
 *
 * @author standadmin
 * @since 2026-03-04
 */
@Data
public class DataPermissionContext {

    /**
     * 包含的数据权限规则
     * 只有在列表中的规则才会生效
     */
    private Set<Class<? extends DataPermissionRule>> includeRules = new HashSet<>();

    /**
     * 排除的数据权限规则
     * 在 include 规则中排除指定的规则
     */
    private Set<Class<? extends DataPermissionRule>> excludeRules = new HashSet<>();

    /**
     * 是否启用数据权限
     */
    private boolean enabled = true;

    /**
     * 添加包含的规则
     *
     * @param ruleClass 规则类型
     */
    public void addIncludeRule(Class<? extends DataPermissionRule> ruleClass) {
        includeRules.add(ruleClass);
    }

    /**
     * 添加排除的规则
     *
     * @param ruleClass 规则类型
     */
    public void addExcludeRule(Class<? extends DataPermissionRule> ruleClass) {
        excludeRules.add(ruleClass);
    }
}
