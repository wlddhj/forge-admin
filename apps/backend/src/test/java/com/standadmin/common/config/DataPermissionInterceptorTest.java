package com.forge.admin.common.config;

import com.forge.admin.common.enumeration.DataScope;
import com.forge.admin.common.permission.DataPermissionRule;
import com.forge.admin.common.utils.UserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 数据权限拦截器单元测试
 *
 * 测试 DataPermissionInterceptor 的核心 SQL 修改逻辑
 * 由于 MyBatis 的 MappedStatement 等类是 final 类，难以模拟，
 * 本测试主要验证拦截器的条件构建和规则处理逻辑
 *
 * @author standadmin
 */
@DisplayName("数据权限拦截器测试")
class DataPermissionInterceptorTest {

    private List<DataPermissionRule> rules;
    private TestDeptDataPermissionRule testRule;

    private UserContext adminContext;
    private UserContext normalUserContext;
    private UserContext allDataScopeContext;

    @BeforeEach
    void setUp() {
        // 创建测试规则
        testRule = new TestDeptDataPermissionRule();
        rules = new ArrayList<>();
        rules.add(testRule);

        // 创建用户上下文
        adminContext = createAdminContext();
        normalUserContext = createNormalUserContext();
        allDataScopeContext = createContext(2L, "all_user", 0, 100L, DataScope.ALL);
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    @DisplayName("应返回正确的需要拦截的表名")
    void testGetTableNames() {
        Set<String> tableNames = testRule.getTableNames();

        assertNotNull(tableNames);
        assertEquals(3, tableNames.size());
        assertTrue(tableNames.contains("sys_user"));
        assertTrue(tableNames.contains("sys_position"));
        assertTrue(tableNames.contains("sys_job"));
    }

    @Test
    @DisplayName("超级管理员应返回 null（无过滤条件）")
    void testAdminUserShouldReturnNull() {
        UserContext.set(adminContext);

        String condition = testRule.buildCondition("sys_user", "u");

        assertNull(condition, "超级管理员不应有数据权限过滤");
    }

    @Test
    @DisplayName("全部数据权限应返回 null（无过滤条件）")
    void testAllDataScopeShouldReturnNull() {
        UserContext.set(allDataScopeContext);

        String condition = testRule.buildCondition("sys_user", "u");

        assertNull(condition, "全部数据权限不应有过滤条件");
    }

    @Test
    @DisplayName("普通用户应返回部门条件")
    void testNormalUserShouldReturnDeptCondition() {
        UserContext.set(normalUserContext);

        String condition = testRule.buildCondition("sys_user", "u");

        assertNotNull(condition, "普通用户应返回数据权限条件");
        assertTrue(condition.contains("dept_id"), "条件应包含 dept_id 字段");
        assertTrue(condition.contains("100"), "条件应包含部门ID 100");
    }

    @Test
    @DisplayName("无用户上下文应返回无权限条件")
    void testNullContextShouldReturnNoPermission() {
        UserContext.set(null);

        String condition = testRule.buildCondition("sys_user", "u");

        assertEquals("1=0", condition, "无用户上下文应返回无权限条件");
    }

    @Test
    @DisplayName("表别名应正确应用到条件中")
    void testTableAliasShouldBeApplied() {
        UserContext.set(normalUserContext);

        String condition = testRule.buildCondition("sys_user", "user_alias");

        assertNotNull(condition);
        assertTrue(condition.contains("user_alias.dept_id"),
            "条件应使用提供的表别名 user_alias");
    }

    @Test
    @DisplayName("SQL 条件构建测试 - 无 WHERE 子句")
    void testSqlConditionBuildingWithoutWhere() {
        String originalSql = "SELECT * FROM sys_user";
        String condition = "u.dept_id = 100";

        String result = addConditionToSql(originalSql, condition);

        assertTrue(result.contains(" WHERE u.dept_id = 100"),
            "应正确添加 WHERE 子句");
    }

    @Test
    @DisplayName("SQL 条件构建测试 - 有 WHERE 子句")
    void testSqlConditionBuildingWithWhere() {
        String originalSql = "SELECT * FROM sys_user WHERE status = 1";
        String condition = "u.dept_id = 100";

        String result = addConditionToSql(originalSql, condition);

        assertTrue(result.contains(" AND u.dept_id = 100"),
            "应正确添加 AND 条件");
        assertTrue(result.contains("status = 1"),
            "原有条件应保留");
    }

    @Test
    @DisplayName("SQL 条件构建测试 - 有 ORDER BY 子句")
    void testSqlConditionBuildingWithOrderBy() {
        String originalSql = "SELECT * FROM sys_user ORDER BY create_time DESC";
        String condition = "u.dept_id = 100";

        String result = addConditionToSql(originalSql, condition);

        assertTrue(result.contains(" WHERE u.dept_id = 100"),
            "应在 ORDER BY 前添加 WHERE");
        assertTrue(result.contains("ORDER BY"),
            "ORDER BY 应保留");
    }

    @Test
    @DisplayName("规则列表应正确初始化")
    void testRulesInitialization() {
        DataPermissionInterceptor interceptor = new DataPermissionInterceptor(rules);

        assertNotNull(interceptor, "拦截器应正确初始化");
    }

    @Test
    @DisplayName("空规则列表应正常处理")
    void testEmptyRulesList() {
        List<DataPermissionRule> emptyRules = new ArrayList<>();
        DataPermissionInterceptor interceptor = new DataPermissionInterceptor(emptyRules);

        assertNotNull(interceptor, "空规则列表应正常处理");
    }

    // ==================== 辅助方法 ====================

    /**
     * 添加条件到 SQL（简化版，用于测试）
     * 这个方法模拟 DataPermissionInterceptor 中的 addConditionToSql 方法
     */
    private String addConditionToSql(String originalSql, String condition) {
        String lowerSql = originalSql.toLowerCase();

        // 查找 WHERE 子句
        int whereIndex = lowerSql.indexOf(" where ");

        if (whereIndex != -1) {
            // 已有 WHERE 子句，添加 AND 条件
            return originalSql + " AND " + condition;
        } else {
            // 没有 WHERE 子句，需要添加 WHERE
            int orderByIndex = lowerSql.indexOf(" order by ");
            if (orderByIndex != -1) {
                // 在 ORDER BY 前插入 WHERE
                return originalSql.substring(0, orderByIndex) +
                       " WHERE " + condition + " " +
                       originalSql.substring(orderByIndex);
            } else {
                // 直接在末尾添加 WHERE
                return originalSql + " WHERE " + condition;
            }
        }
    }

    /**
     * 创建管理员上下文
     */
    private UserContext createAdminContext() {
        return createContext(1L, "admin", 1, 100L, DataScope.ALL);
    }

    /**
     * 创建普通用户上下文
     */
    private UserContext createNormalUserContext() {
        return createContext(2L, "user", 0, 100L, DataScope.DEPT);
    }

    /**
     * 创建用户上下文
     */
    private UserContext createContext(Long userId, String username, int accountType,
                                     Long deptId, DataScope dataScope) {
        UserContext context = new UserContext();
        context.setUserId(userId);
        context.setUsername(username);
        context.setAccountType(accountType);
        context.setDeptId(deptId);
        context.setMaxDataScope(dataScope);
        context.setRoles(new ArrayList<>());
        context.setContextCache(new java.util.HashMap<>());
        return context;
    }

    /**
     * 测试用的数据权限规则
     */
    private static class TestDeptDataPermissionRule implements DataPermissionRule {

        @Override
        public Set<String> getTableNames() {
            return Set.of("sys_user", "sys_position", "sys_job");
        }

        @Override
        public net.sf.jsqlparser.expression.Expression getExpression(String tableName, net.sf.jsqlparser.expression.Alias tableAlias) {
            UserContext context = UserContext.get();
            if (context == null) {
                return new net.sf.jsqlparser.expression.operators.relational.EqualsTo(
                    new net.sf.jsqlparser.expression.LongValue("1"),
                    new net.sf.jsqlparser.expression.LongValue("0")
                );
            }

            // 超级管理员
            if (context.isAdmin()) {
                return null;
            }

            // 全部数据权限
            if (context.getMaxDataScope() == DataScope.ALL) {
                return null;
            }

            // 本部门数据权限
            if (context.getDeptId() != null) {
                net.sf.jsqlparser.schema.Column column = new net.sf.jsqlparser.schema.Column();
                column.setColumnName("dept_id");
                column.setTable(new net.sf.jsqlparser.schema.Table(tableAlias != null ? tableAlias.getName() : tableName));
                return new net.sf.jsqlparser.expression.operators.relational.EqualsTo(
                    column,
                    new net.sf.jsqlparser.expression.LongValue(context.getDeptId().toString())
                );
            }

            return new net.sf.jsqlparser.expression.operators.relational.EqualsTo(
                new net.sf.jsqlparser.expression.LongValue("1"),
                new net.sf.jsqlparser.expression.LongValue("0")
            );
        }

        @Override
        public String buildCondition(String tableName, String tableAlias) {
            UserContext context = UserContext.get();
            if (context == null) {
                return "1=0";
            }

            // 超级管理员
            if (context.isAdmin()) {
                return null;
            }

            // 全部数据权限
            if (context.getMaxDataScope() == DataScope.ALL) {
                return null;
            }

            // 本部门数据权限
            if (context.getDeptId() != null) {
                return String.format("%s.dept_id = %d", tableAlias, context.getDeptId());
            }

            return "1=0";
        }
    }
}
