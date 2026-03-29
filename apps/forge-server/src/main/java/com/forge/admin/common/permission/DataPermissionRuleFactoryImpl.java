package com.forge.admin.common.permission;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 数据权限规则工厂实现
 *
 * 集中管理所有数据权限规则，支持 include/exclude 过滤
 * 基于 shi9-boot 设计
 *
 * @author standadmin
 * @since 2026-03-04
 */
@Slf4j
@Component
public class DataPermissionRuleFactoryImpl implements DataPermissionRuleFactory {

    private final Map<Class<?>, DataPermissionRule> rules = new ConcurrentHashMap<>();

    public DataPermissionRuleFactoryImpl(List<DataPermissionRule> initialRules) {
        if (initialRules != null) {
            for (var rule : initialRules) {
                rules.put(rule.getClass(), rule);
                log.debug("[规则工厂] 注册规则: {}", rule.getClass().getSimpleName());
            }
        }
        log.info("[规则工厂] 初始化完成，注册规则数量: {}", rules.size());
    }

    @Override
    public Set<DataPermissionRule> getAllRules() {
        return new LinkedHashSet<>(rules.values());
    }

    @Override
    public Set<DataPermissionRule> getEnabledRules(String mappedStatementId) {
        var context = DataPermissionContextHolder.peek();

        // 如果没有上下文或没有指定 include 规则，返回所有规则
        if (context == null || context.getIncludeRules().isEmpty()) {
            return getAllRules();
        }

        // 根据 include 规则过滤
        Set<DataPermissionRule> enabled = new LinkedHashSet<>();
        for (var ruleClass : context.getIncludeRules()) {
            var rule = rules.get(ruleClass);
            if (rule != null) {
                // 检查是否在 exclude 列表中
                if (!context.getExcludeRules().contains(ruleClass)) {
                    enabled.add(rule);
                }
            }
        }

        return enabled;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends DataPermissionRule> T getRule(Class<T> ruleClass) {
        return (T) rules.get(ruleClass);
    }
}
