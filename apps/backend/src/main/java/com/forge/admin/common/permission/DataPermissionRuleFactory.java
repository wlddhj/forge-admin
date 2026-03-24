package com.forge.admin.common.permission;

import java.util.Set;

/**
 * 数据权限规则工厂接口
 *
 * 提供规则管理功能，支持动态获取和过滤规则
 * 基于 shi9-boot 设计
 *
 * @author standadmin
 * @since 2026-03-04
 */
public interface DataPermissionRuleFactory {

    /**
     * 获取所有规则
     *
     * @return 所有规则集合
     */
    Set<DataPermissionRule> getAllRules();

    /**
     * 获取指定 MappedStatement 生效的规则
     *
     * @param mappedStatementId MappedStatement ID
     * @return 生效的规则集合
     */
    Set<DataPermissionRule> getEnabledRules(String mappedStatementId);

    /**
     * 获取指定类型的规则
     *
     * @param ruleClass 规则类型
     * @param <T> 规则类型泛型
     * @return 规则实例，不存在返回 null
     */
    <T extends DataPermissionRule> T getRule(Class<T> ruleClass);
}
