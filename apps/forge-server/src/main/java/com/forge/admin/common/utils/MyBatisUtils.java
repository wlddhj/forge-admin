package com.forge.admin.common.utils;

import lombok.experimental.UtilityClass;
import net.sf.jsqlparser.expression.*;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.expression.operators.conditional.OrExpression;
import net.sf.jsqlparser.expression.operators.relational.EqualsTo;
import net.sf.jsqlparser.expression.operators.relational.InExpression;
import net.sf.jsqlparser.expression.operators.relational.ExpressionList;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;

/**
 * MyBatis 工具类
 *
 * 提供 JSQLParser Expression 构建的便捷方法
 * 基于 shi9-boot 设计
 *
 * @author standadmin
 * @since 2026-03-04
 */
@UtilityClass
public class MyBatisUtils {

    /**
     * 构建列表达式
     *
     * @param tableName 表名
     * @param tableAlias 表别名
     * @param columnName 列名
     * @return 列表达式
     */
    public Column buildColumn(String tableName, String tableAlias, String columnName) {
        Column column = new Column();
        column.setColumnName(columnName);
        if (tableAlias != null) {
            column.setTable(new Table(tableAlias));
        } else if (tableName != null) {
            column.setTable(new Table(tableName));
        }
        return column;
    }

    /**
     * 构建 IN 表达式
     *
     * @param tableName 表名
     * @param tableAlias 表别名
     * @param columnName 列名
     * @param values IN 的值
     * @return IN 表达式
     */
    public Expression buildInExpression(String tableName, String tableAlias,
                                       String columnName, Long... values) {
        if (values == null || values.length == 0) {
            return null;
        }

        // 使用 JSQLParser 4.9 的 ExpressionList
        ExpressionList<net.sf.jsqlparser.expression.Expression> list =
            new ExpressionList<>();

        for (var v : values) {
            list.add(new LongValue(v.toString()));
        }

        return new InExpression(
            buildColumn(tableName, tableAlias, columnName),
            new Parenthesis(list)
        );
    }

    /**
     * 构建 IN 表达式（字符串值）
     *
     * @param tableName 表名
     * @param tableAlias 表别名
     * @param columnName 列名
     * @param values IN 的值
     * @return IN 表达式
     */
    public Expression buildInExpressionString(String tableName, String tableAlias,
                                             String columnName, String... values) {
        if (values == null || values.length == 0) {
            return null;
        }

        ExpressionList<net.sf.jsqlparser.expression.Expression> list =
            new ExpressionList<>();

        for (var v : values) {
            list.add(new StringValue(v));
        }

        return new InExpression(
            buildColumn(tableName, tableAlias, columnName),
            new Parenthesis(list)
        );
    }

    /**
     * 构建 = 表达式
     *
     * @param tableName 表名
     * @param tableAlias 表别名
     * @param columnName 列名
     * @param value 值
     * @return 等于表达式
     */
    public Expression buildEqualsExpression(String tableName, String tableAlias,
                                           String columnName, Long value) {
        return new EqualsTo(
            buildColumn(tableName, tableAlias, columnName),
            new LongValue(value.toString())
        );
    }

    /**
     * 构建 AND 表达式
     *
     * @param expressions 条件表达式数组
     * @return AND 表达式
     */
    public Expression buildAndExpression(Expression... expressions) {
        if (expressions == null || expressions.length == 0) {
            return null;
        }
        if (expressions.length == 1) {
            return expressions[0];
        }

        Expression result = expressions[0];
        for (int i = 1; i < expressions.length; i++) {
            if (expressions[i] != null) {
                result = new AndExpression(result, expressions[i]);
            }
        }
        return result;
    }

    /**
     * 构建 OR 表达式
     *
     * @param expressions 条件表达式数组
     * @return OR 表达式
     */
    public Expression buildOrExpression(Expression... expressions) {
        if (expressions == null || expressions.length == 0) {
            return null;
        }
        if (expressions.length == 1) {
            return expressions[0];
        }

        Expression result = expressions[0];
        for (int i = 1; i < expressions.length; i++) {
            if (expressions[i] != null) {
                result = new OrExpression(result, expressions[i]);
            }
        }
        return result;
    }

    /**
     * 构建 "1=0" 表达式（无权限）
     *
     * @return 无权限表达式
     */
    public Expression buildNoPermissionExpression() {
        return new EqualsTo(new LongValue("1"), new LongValue("0"));
    }

    /**
     * 构建 IN 表达式（使用 Alias 对象）
     */
    public Expression buildInExpression(String tableName, net.sf.jsqlparser.expression.Alias tableAlias,
                                       String columnName, Long... values) {
        String aliasStr = tableAlias != null ? tableAlias.getName() : null;
        return buildInExpression(tableName, aliasStr, columnName, values);
    }

    /**
     * 构建 = 表达式（使用 Alias 对象）
     */
    public Expression buildEqualsExpression(String tableName, net.sf.jsqlparser.expression.Alias tableAlias,
                                           String columnName, Long value) {
        String aliasStr = tableAlias != null ? tableAlias.getName() : null;
        return buildEqualsExpression(tableName, aliasStr, columnName, value);
    }
}
