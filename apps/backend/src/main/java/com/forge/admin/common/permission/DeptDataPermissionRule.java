package com.forge.admin.common.permission;

import com.forge.admin.common.enumeration.DataScope;
import com.forge.admin.common.utils.DeptDataScopeUtils;
import com.forge.admin.common.utils.MyBatisUtils;
import com.forge.admin.common.utils.UserContext;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.expression.Alias;
import net.sf.jsqlparser.expression.Expression;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 部门数据权限规则
 *
 * 基于 shi9-boot 设计，支持 5 种数据权限类型：
 * - 全部数据权限
 * - 自定义数据权限 (CUSTOM)
 * - 本部门数据权限 (DEPT)
 * - 本部门及以下数据权限 (DEPT_AND_CHILD)
 * - 仅本人数据权限 (SELF)
 *
 * @author standadmin
 */
@Slf4j
@Component
public class DeptDataPermissionRule implements DataPermissionRule {

    private static final String CONTEXT_KEY = "DEPT_DATA_PERMISSION";

    @Override
    public Set<String> getTableNames() {
        // 需要进行数据权限控制的表（sys_job 表暂时移除，因为它没有 dept_id 字段）
        return Set.of("sys_user", "sys_position");
    }

    @Override
    public Expression getExpression(String tableName, Alias tableAlias) {
        // 1. 获取当前用户上下文
        UserContext context = UserContext.get();
        if (context == null) {
            log.debug("[数据权限规则] 用户上下文为空，跳过数据权限过滤");
            return null;  // 返回 null 表示不添加过滤条件，而不是 1=0
        }

        // 2. 检查是否为超级管理员
        if (context.isAdmin()) {
            log.debug("[数据权限规则] 超级管理员，不进行数据权限过滤");
            return null;
        }

        // 3. 从上下文缓存获取数据权限信息
        DeptDataScopeInfo dataScopeInfo = context.getContext(CONTEXT_KEY, DeptDataScopeInfo.class);

        // 4. 如果缓存中没有，计算并缓存
        if (dataScopeInfo == null) {
            log.debug("[数据权限规则] 缓存未命中，计算数据权限信息");
            dataScopeInfo = calculateDataScopeInfo(context);
            context.setContext(CONTEXT_KEY, dataScopeInfo);
        }

        // 5. 根据数据权限范围构建表达式
        return buildExpression(tableAlias, dataScopeInfo, context);
    }

    @Override
    public String buildCondition(String tableName, String tableAlias) {
        Expression expr = getExpression(tableName,
            tableAlias != null ? new Alias(tableAlias) : null);
        return expr != null ? expr.toString() : null;
    }

    /**
     * 计算数据权限信息（在 JWT 认证后只计算一次，然后缓存）
     */
    private DeptDataScopeInfo calculateDataScopeInfo(UserContext context) {
        DataScope maxDataScope = context.getMaxDataScope();
        Long deptId = context.getDeptId();

        log.debug("[数据权限规则] 计算数据权限 - 最大权限范围: {}, 部门ID: {}", maxDataScope, deptId);

        List<Long> deptIds = new ArrayList<>();

        switch (maxDataScope) {
            case ALL:
                log.debug("[数据权限规则] 全部数据权限");
                DeptDataScopeInfo allInfo = new DeptDataScopeInfo();
                allInfo.setAll(true);
                return allInfo;

            case CUSTOM:
                deptIds = getCustomDeptIds(context);
                log.debug("[数据权限规则] 自定义数据权限 - 部门IDs: {}", deptIds);
                DeptDataScopeInfo customInfo = new DeptDataScopeInfo();
                customInfo.setDeptIds(deptIds);
                return customInfo;

            case DEPT:
                if (deptId != null) {
                    deptIds = List.of(deptId);
                }
                log.debug("[数据权限规则] 本部门数据权限 - 部门ID: {}", deptId);
                DeptDataScopeInfo deptInfo = new DeptDataScopeInfo();
                deptInfo.setDeptIds(deptIds);
                return deptInfo;

            case DEPT_AND_CHILD:
                if (deptId != null) {
                    deptIds = DeptDataScopeUtils.getChildDeptIds(deptId);
                }
                log.debug("[数据权限规则] 本部门及以下数据权限 - 部门IDs: {}", deptIds);
                DeptDataScopeInfo deptAndChildInfo = new DeptDataScopeInfo();
                deptAndChildInfo.setDeptIds(deptIds);
                return deptAndChildInfo;

            case SELF:
                log.debug("[数据权限规则] 仅本人数据权限");
                DeptDataScopeInfo selfInfo = new DeptDataScopeInfo();
                selfInfo.setSelf(true);
                return selfInfo;

            default:
                log.warn("[数据权限规则] 未知的数据权限范围: {}", maxDataScope);
                return new DeptDataScopeInfo();
        }
    }

    /**
     * 获取自定义部门ID列表
     */
    private List<Long> getCustomDeptIds(UserContext context) {
        if (context.getRoles() == null) {
            return Collections.emptyList();
        }

        return context.getRoles().stream()
            .filter(r -> DataScope.CUSTOM.equals(r.getDataScope()))
            .filter(r -> r.getDeptIds() != null)
            .flatMap(r -> r.getDeptIds().stream())
            .distinct()
            .collect(Collectors.toList());
    }

    /**
     * 构建 SQL 条件（兼容旧代码）
     */
    private String buildSqlCondition(String tableAlias, DeptDataScopeInfo info, UserContext context) {
        Expression expr = buildExpression(
            tableAlias != null ? new Alias(tableAlias) : null,
            info, context
        );
        return expr != null ? expr.toString() : null;
    }

    /**
     * 构建数据权限表达式
     *
     * @param tableAlias 表别名（JSQLParser 的 Alias 对象）
     * @param info 数据权限信息
     * @param context 用户上下文
     * @return Expression 对象
     */
    private Expression buildExpression(Alias tableAlias, DeptDataScopeInfo info, UserContext context) {
        // 全部权限
        if (info.isAll()) {
            return null;
        }

        List<Expression> conditions = new ArrayList<>();

        // 部门权限
        if (info.getDeptIds() != null && !info.getDeptIds().isEmpty()) {
            Expression inExpr = MyBatisUtils.buildInExpression(
                null, tableAlias, "dept_id",
                info.getDeptIds().toArray(new Long[0])
            );
            if (inExpr != null) {
                conditions.add(inExpr);
            }
        }

        // 仅本人权限
        if (info.isSelf()) {
            Expression equalsExpr = MyBatisUtils.buildEqualsExpression(
                null, tableAlias, "id", context.getUserId()
            );
            if (equalsExpr != null) {
                conditions.add(equalsExpr);
            }
        }

        // 组合条件：(dept_id IN (...) OR id = ...)
        if (conditions.isEmpty()) {
            log.debug("[数据权限规则] 无任何权限条件");
            return MyBatisUtils.buildNoPermissionExpression();
        } else if (conditions.size() == 1) {
            Expression condition = conditions.get(0);
            log.debug("[数据权限规则] 单一条件: {}", condition);
            return condition;
        } else {
            Expression combined = MyBatisUtils.buildOrExpression(conditions.toArray(new Expression[0]));
            log.debug("[数据权限规则] 组合条件: {}", combined);
            return combined;
        }
    }

    /**
     * 数据权限信息（内部类）
     * 用于缓存计算结果，避免重复计算
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DeptDataScopeInfo {
        /**
         * 全部权限
         */
        private boolean all;

        /**
         * 部门ID列表
         */
        private List<Long> deptIds;

        /**
         * 仅本人权限
         */
        private boolean self;
    }
}
