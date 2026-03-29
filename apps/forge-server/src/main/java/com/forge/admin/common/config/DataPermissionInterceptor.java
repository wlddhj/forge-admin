package com.forge.admin.common.config;

import com.baomidou.mybatisplus.extension.plugins.inner.InnerInterceptor;
import com.forge.admin.common.annotation.DataPermission;
import com.forge.admin.common.permission.DataPermissionRule;
import com.forge.admin.common.utils.UserContext;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 数据权限拦截器
 *
 * 基于 shi9-boot 设计，使用规则模式 + 上下文缓存
 * 支持多种数据权限规则，可扩展
 *
 * @author standadmin
 */
@Slf4j
@Component
public class DataPermissionInterceptor implements InnerInterceptor {

    private final List<DataPermissionRule> rules;

    @Autowired
    public DataPermissionInterceptor(List<DataPermissionRule> rules) {
        this.rules = rules != null ? new ArrayList<>(rules) : new ArrayList<>();
        log.info("[数据权限拦截器] 初始化完成，注册规则数量: {}", this.rules.size());
    }

    @Override
    public void beforeQuery(Executor executor, MappedStatement ms, Object parameter,
                           RowBounds rowBounds, ResultHandler resultHandler, BoundSql boundSql) {
        log.debug("[数据权限拦截器] 触发查询 - MappedStatement: {}", ms.getId());

        // 1. 检查是否为 COUNT 查询（MyBatis-Plus 分页自动生成）
        String sql = boundSql.getSql().trim();
        boolean isCountQuery = sql.toUpperCase().startsWith("SELECT COUNT");

        // 2. 检查是否有数据权限注解
        DataPermission dataPermission = getDataPermissionAnnotation(ms.getId(), isCountQuery);
        if (dataPermission == null) {
            log.debug("[数据权限拦截器] 未找到数据权限注解，跳过");
            return;
        }

        // 检查是否禁用数据权限
        if (!dataPermission.enable()) {
            log.debug("[数据权限拦截器] 数据权限已禁用，跳过");
            return;
        }

        // 对于 COUNT 查询，记录日志以便调试
        if (isCountQuery) {
            log.debug("[数据权限拦截器] 检测到 COUNT 查询，应用数据权限过滤");
        }

        // 2. 获取用户上下文
        UserContext context = UserContext.get();
        if (context == null) {
            log.info("[数据权限拦截器] 用户上下文为空，跳过");
            return;
        }

        log.debug("[数据权限拦截器] 当前用户: {}, deptId: {}, maxDataScope: {}",
                context.getUsername(), context.getDeptId(), context.getMaxDataScope());

        // 3. 检查是否为超级管理员
        if (context.isAdmin()) {
            log.debug("[数据权限拦截器] 超级管理员，跳过数据权限检查");
            return;
        }

        // 4. 应用所有数据权限规则
        List<String> conditions = new ArrayList<>();
        String originalSql = boundSql.getSql();

        for (DataPermissionRule rule : rules) {
            // 解析 SQL 获取表名和别名
            Map<String, String> tables = parseTableNames(originalSql);

            // 对每个需要拦截的表生成条件
            for (Map.Entry<String, String> entry : tables.entrySet()) {
                String tableName = entry.getKey();
                String tableAlias = entry.getValue();

                if (rule.getTableNames().contains(tableName)) {
                    String condition = rule.buildCondition(tableName, tableAlias);
                    if (condition != null && !condition.isEmpty()) {
                        conditions.add(condition);
                        log.debug("[数据权限拦截器] 规则 {} 生效 - 表: {}, 别名: {}, 条件: {}",
                                rule.getClass().getSimpleName(), tableName, tableAlias, condition);
                    }
                }
            }
        }

        // 5. 组合所有条件并修改 SQL
        if (!conditions.isEmpty()) {
            String combinedCondition;
            if (conditions.size() == 1) {
                combinedCondition = conditions.get(0);
            } else {
                combinedCondition = "(" + String.join(") AND (", conditions) + ")";
            }

            String modifiedSql = addConditionToSql(originalSql, combinedCondition);

            if (!originalSql.equals(modifiedSql)) {
                try {
                    Field field = boundSql.getClass().getDeclaredField("sql");
                    field.setAccessible(true);
                    field.set(boundSql, modifiedSql);
                    log.info("[数据权限拦截器] 数据权限生效 - 条件: {}", combinedCondition);
                    log.debug("[数据权限拦截器] 原始SQL: {}", originalSql);
                    log.debug("[数据权限拦截器] 修改后SQL: {}", modifiedSql);
                } catch (Exception e) {
                    log.error("[数据权限拦截器] 修改 BoundSql 失败", e);
                }
            }
        } else {
            log.debug("[数据权限拦截器] 没有数据权限规则生效");
        }
    }

    /**
     * 解析 SQL 获取表名和别名（简化版）
     *
     * 注意：这是简化的实现，使用正则表达式解析
     * 生产环境建议使用 JSQLParser 进行更准确的解析
     */
    private Map<String, String> parseTableNames(String sql) {
        Map<String, String> tables = new HashMap<>();

        // 匹配 FROM table_name alias 或 JOIN table_name alias
        Pattern pattern = Pattern.compile("(?:FROM|JOIN)\\s+(\\w+)(?:\\s+(\\w+))?", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(sql);

        while (matcher.find()) {
            String tableName = matcher.group(1);
            String alias = matcher.group(2);
            if (alias == null || alias.isEmpty()) {
                alias = tableName;
            }
            tables.put(tableName, alias);
        }

        return tables;
    }

    /**
     * 添加条件到 SQL
     * 使用字符串操作而不是 JSQLParser，以简化实现
     */
    private String addConditionToSql(String originalSql, String condition) {
        try {
            String lowerSql = originalSql.toLowerCase();

            // 查找 WHERE 子句的位置
            String whereKeyword = findWhereKeyword(originalSql);
            int whereIndex = -1;
            if (whereKeyword != null) {
                whereIndex = lowerSql.indexOf(whereKeyword.toLowerCase());
            }

            StringBuilder modifiedSql;

            if (whereIndex != -1) {
                // 已有 WHERE 子句，添加 AND 条件
                int whereEndIndex = whereIndex + whereKeyword.length();
                String afterWhere = originalSql.substring(whereEndIndex).trim();

                modifiedSql = new StringBuilder(originalSql);
                if (!afterWhere.isEmpty()) {
                    // WHERE 后面已有条件，添加 AND
                    modifiedSql.append(" AND ").append(condition);
                } else {
                    // WHERE 后面没有条件
                    modifiedSql.append(" ").append(condition);
                }
            } else {
                // 没有 WHERE 子句，需要添加 WHERE
                int insertIndex = findInsertIndex(originalSql);

                if (insertIndex != -1) {
                    // 在 GROUP BY/HAVING/ORDER BY/LIMIT 前插入 WHERE
                    modifiedSql = new StringBuilder(originalSql);
                    modifiedSql.insert(insertIndex, " WHERE " + condition + " ");
                } else {
                    // 直接在 SQL 末尾添加 WHERE
                    modifiedSql = new StringBuilder(originalSql);
                    modifiedSql.append(" WHERE ").append(condition);
                }
            }

            return modifiedSql.toString();
        } catch (Exception e) {
            log.error("[数据权限拦截器] 添加条件到 SQL 失败: {}", originalSql, e);
            return originalSql;
        }
    }

    /**
     * 查找 SQL 中的 WHERE 关键字（支持大小写）
     */
    private String findWhereKeyword(String sql) {
        String[] patterns = {" WHERE ", " where ", "\nWHERE ", "\nwhere ", "\tWHERE ", "\twhere "};
        for (String pattern : patterns) {
            if (sql.contains(pattern)) {
                return pattern.trim();
            }
        }
        // 也可能是换行后直接跟 WHERE
        if (sql.matches("(?i).*\nwhere\\s.*")) {
            return "WHERE";
        }
        return null;
    }

    /**
     * 查找插入 WHERE 子句的位置
     * 在 GROUP BY, HAVING, ORDER BY, LIMIT 之前
     */
    private int findInsertIndex(String sql) {
        String lowerSql = sql.toLowerCase();

        // 查找这些关键字的最前位置
        String[] keywords = {" group by ", " having ", " order by ", " limit "};
        int minIndex = -1;

        for (String keyword : keywords) {
            int index = lowerSql.indexOf(keyword);
            if (index != -1 && (minIndex == -1 || index < minIndex)) {
                minIndex = index;
            }
        }

        // 也检查换行后的情况
        int[] newlineIndices = {
            lowerSql.indexOf("\ngroup"),
            lowerSql.indexOf("\nhaving"),
            lowerSql.indexOf("\norder"),
            lowerSql.indexOf("\nlimit")
        };

        for (int index : newlineIndices) {
            if (index != -1 && (minIndex == -1 || index < minIndex)) {
                minIndex = index;
            }
        }

        return minIndex;
    }

    /**
     * 获取数据权限注解
     * 处理 MyBatis-Plus 分页的 !count 后缀和自动 COUNT 查询
     */
    private DataPermission getDataPermissionAnnotation(String mappedStatementId, boolean isCountQuery) {
        try {
            String actualMethodId = mappedStatementId;

            // 处理 MyBatis-Plus 分页查询的 !count 后缀
            if (mappedStatementId.contains("!count")) {
                actualMethodId = mappedStatementId.replace("!count", "");
            }

            // 对于 MyBatis-Plus 自动生成的 COUNT 查询，尝试匹配原始方法
            // 例如: selectUserPageWithPermission 的 COUNT 查询
            if (isCountQuery && !actualMethodId.contains("Count")) {
                // COUNT 查询使用相同的 MappedStatement ID，所以直接使用即可
                // 不需要额外处理，因为注解在原始方法上
            }

            String className = actualMethodId.substring(0, actualMethodId.lastIndexOf('.'));
            String methodName = actualMethodId.substring(actualMethodId.lastIndexOf('.') + 1);

            Class<?> clazz = Class.forName(className);
            Method[] methods = clazz.getDeclaredMethods();

            for (Method method : methods) {
                if (method.getName().equals(methodName)) {
                    DataPermission annotation = method.getAnnotation(DataPermission.class);
                    if (annotation != null) {
                        return annotation;
                    }
                }
            }

            // 如果是 COUNT 查询且没有找到注解，可能是因为 MyBatis-Plus 自动生成
            // 尝试从缓存中查找（假设同一个查询的 COUNT 和 SELECT 使用相同的注解）
            if (isCountQuery) {
                log.debug("[数据权限拦截器] COUNT 查询未找到注解，尝试从原始方法推断");
                // 这里可以添加缓存逻辑，暂时返回 null 让 COUNT 查询也不被过滤
            }

        } catch (Exception e) {
            log.error("[数据权限拦截器] 获取数据权限注解失败: {}", mappedStatementId, e);
        }

        return null;
    }
}
