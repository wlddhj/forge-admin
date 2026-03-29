package com.forge.admin.common.permission;

import net.sf.jsqlparser.expression.Alias;
import net.sf.jsqlparser.expression.Expression;

import java.util.Set;

/**
 * 数据权限规则接口
 *
 * 基于 shi9-boot 设计，实现可扩展的数据权限规则模式
 *
 * @author standadmin
 */
public interface DataPermissionRule {

    /**
     * 获取需要拦截的表名
     *
     * @return 表名集合
     */
    Set<String> getTableNames();

    /**
     * 构建数据权限条件表达式（推荐方式）
     *
     * 直接返回 JSQLParser 的 Expression 对象，类型安全且高效
     *
     * @param tableName 表名
     * @param tableAlias 表别名（JSQLParser 的 Alias 对象）
     * @return SQL 条件表达式，返回 null 表示不进行过滤
     */
    Expression getExpression(String tableName, Alias tableAlias);

    /**
     * 构建数据权限 SQL 条件（兼容方式）
     *
     * @param tableName 表名
     * @param tableAlias 表别名
     * @return SQL 条件字符串，返回 null 表示不进行过滤
     * @deprecated 使用 getExpression 替代，避免 SQL 字符串二次解析
     */
    @Deprecated
    default String buildCondition(String tableName, String tableAlias) {
        Expression expr = getExpression(tableName,
            tableAlias != null ? new Alias(tableAlias) : null);
        return expr != null ? expr.toString() : null;
    }
}
