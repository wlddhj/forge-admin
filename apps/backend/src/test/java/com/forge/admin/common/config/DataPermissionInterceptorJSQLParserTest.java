package com.forge.admin.common.config;

import com.forge.admin.common.enumeration.DataScope;
import com.forge.admin.common.permission.DataPermissionRule;
import com.forge.admin.common.utils.UserContext;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.FromItem;
import net.sf.jsqlparser.statement.select.Join;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JSQLParser 数据权限拦截器单元测试
 *
 * 测试 DataPermissionInterceptorJSQLParser 的核心 SQL 解析逻辑
 * 使用 JSQLParser 进行准确的 SQL 解析和修改
 *
 * @author standadmin
 * @since 2026-03-04
 */
@DisplayName("JSQLParser 数据权限拦截器测试")
class DataPermissionInterceptorJSQLParserTest {

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
        assertTrue(tableNames.contains("sys_dept"));
        assertTrue(tableNames.contains("sys_position"));
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
    @DisplayName("JSQLParser - 简单 SELECT 解析")
    void testJSQLParserSimpleSelect() throws JSQLParserException {
        String sql = "SELECT * FROM sys_user";

        Statement statement = CCJSqlParserUtil.parse(sql);
        assertTrue(statement instanceof Select, "应解析为 Select 语句");

        Select select = (Select) statement;
        PlainSelect plainSelect = (PlainSelect) select.getSelectBody();

        FromItem fromItem = plainSelect.getFromItem();
        assertTrue(fromItem instanceof Table, "FROM 项应为 Table");
    }

    @Test
    @DisplayName("JSQLParser - 带 WHERE 子句的 SELECT 解析")
    void testJSQLParserSelectWithWhere() throws JSQLParserException {
        String sql = "SELECT * FROM sys_user WHERE status = 1";

        Statement statement = CCJSqlParserUtil.parse(sql);
        Select select = (Select) statement;
        PlainSelect plainSelect = (PlainSelect) select.getSelectBody();

        Expression where = plainSelect.getWhere();
        assertNotNull(where, "应存在 WHERE 条件");
        assertTrue(where.toString().contains("status"), "WHERE 条件应包含 status");
    }

    @Test
    @DisplayName("JSQLParser - 带 JOIN 的 SELECT 解析")
    void testJSQLParserSelectWithJoin() throws JSQLParserException {
        String sql = "SELECT u.*, d.name FROM sys_user u LEFT JOIN sys_dept d ON u.dept_id = d.id";

        Statement statement = CCJSqlParserUtil.parse(sql);
        Select select = (Select) statement;
        PlainSelect plainSelect = (PlainSelect) select.getSelectBody();

        // 检查主表
        FromItem fromItem = plainSelect.getFromItem();
        assertTrue(fromItem instanceof Table, "主表应为 Table");
        Table mainTable = (Table) fromItem;
        assertEquals("sys_user", mainTable.getName(), "主表名应为 sys_user");
        assertEquals("u", mainTable.getAlias().getName(), "主表别名应为 u");

        // 检查 JOIN 表
        List<Join> joins = plainSelect.getJoins();
        assertNotNull(joins, "应存在 JOIN");
        assertEquals(1, joins.size(), "应有一个 JOIN");

        Join join = joins.get(0);
        FromItem rightItem = join.getRightItem();
        assertTrue(rightItem instanceof Table, "JOIN 表应为 Table");
        Table joinedTable = (Table) rightItem;
        assertEquals("sys_dept", joinedTable.getName(), "JOIN 表名应为 sys_dept");
        assertEquals("d", joinedTable.getAlias().getName(), "JOIN 表别名应为 d");
    }

    @Test
    @DisplayName("JSQLParser - 添加 WHERE 条件到无 WHERE 的 SQL")
    void testJSQLParserAddWhereToSqlWithoutWhere() throws JSQLParserException {
        String originalSql = "SELECT * FROM sys_user";

        Statement statement = CCJSqlParserUtil.parse(originalSql);
        Select select = (Select) statement;
        PlainSelect plainSelect = (PlainSelect) select.getSelectBody();

        // 添加 WHERE 条件
        Expression newCondition = CCJSqlParserUtil.parseCondExpression("u.dept_id = 100");
        plainSelect.setWhere(newCondition);

        String modifiedSql = select.toString();
        assertTrue(modifiedSql.contains("WHERE"), "修改后的 SQL 应包含 WHERE");
        assertTrue(modifiedSql.contains("dept_id = 100"), "修改后的 SQL 应包含新条件");
    }

    @Test
    @DisplayName("JSQLParser - 添加条件到已有 WHERE 的 SQL")
    void testJSQLParserAddConditionToSqlWithExistingWhere() throws JSQLParserException {
        String originalSql = "SELECT * FROM sys_user WHERE status = 1";

        Statement statement = CCJSqlParserUtil.parse(originalSql);
        Select select = (Select) statement;
        PlainSelect plainSelect = (PlainSelect) select.getSelectBody();

        // 获取现有 WHERE 条件
        Expression existingWhere = plainSelect.getWhere();
        assertNotNull(existingWhere, "应存在现有 WHERE 条件");

        // 添加新条件（AND 连接）
        Expression newCondition = CCJSqlParserUtil.parseCondExpression("dept_id = 100");
        AndExpression combined = new AndExpression(existingWhere, newCondition);
        plainSelect.setWhere(combined);

        String modifiedSql = select.toString();
        assertTrue(modifiedSql.contains("AND"), "修改后的 SQL 应包含 AND");
        assertTrue(modifiedSql.contains("status = 1"), "修改后的 SQL 应保留原条件");
        assertTrue(modifiedSql.contains("dept_id = 100"), "修改后的 SQL 应包含新条件");
    }

    @Test
    @DisplayName("JSQLParser - 复杂 SQL 解析（JOIN + WHERE + ORDER BY）")
    void testJSQLParserComplexSql() throws JSQLParserException {
        String sql = "SELECT u.*, d.name " +
                     "FROM sys_user u " +
                     "LEFT JOIN sys_dept d ON u.dept_id = d.id " +
                     "WHERE u.status = 1 " +
                     "ORDER BY u.create_time DESC";

        Statement statement = CCJSqlParserUtil.parse(sql);
        Select select = (Select) statement;
        PlainSelect plainSelect = (PlainSelect) select.getSelectBody();

        // 验证各部分存在
        assertNotNull(plainSelect.getFromItem(), "应存在 FROM 子句");
        assertNotNull(plainSelect.getJoins(), "应存在 JOIN");
        assertNotNull(plainSelect.getWhere(), "应存在 WHERE");
        assertNotNull(plainSelect.getOrderByElements(), "应存在 ORDER BY");

        // 添加数据权限条件
        Expression existingWhere = plainSelect.getWhere();
        Expression newCondition = CCJSqlParserUtil.parseCondExpression("u.dept_id = 100");
        AndExpression combined = new AndExpression(existingWhere, newCondition);
        plainSelect.setWhere(combined);

        String modifiedSql = select.toString();
        assertTrue(modifiedSql.contains("AND"), "修改后的 SQL 应包含 AND");
        assertTrue(modifiedSql.contains("ORDER BY"), "修改后的 SQL 应保留 ORDER BY");
    }

    @Test
    @DisplayName("JSQLParser - 字符串中含 FROM 关键字不应被误解析")
    void testJSQLParserFromStringInComment() throws JSQLParserException {
        // 这是正则表达式版本容易出错的情况
        String sql = "SELECT * FROM sys_user WHERE remark LIKE '%This is FROM table%'";

        // JSQLParser 应能正确解析
        Statement statement = CCJSqlParserUtil.parse(sql);
        Select select = (Select) statement;
        PlainSelect plainSelect = (PlainSelect) select.getSelectBody();

        FromItem fromItem = plainSelect.getFromItem();
        assertTrue(fromItem instanceof Table, "应正确识别主表");
        assertEquals("sys_user", ((Table) fromItem).getName(), "主表应为 sys_user");

        // WHERE 条件应包含 LIKE 表达式
        Expression where = plainSelect.getWhere();
        assertNotNull(where, "应存在 WHERE 条件");
    }

    @Test
    @DisplayName("规则列表应正确初始化")
    void testRulesInitialization() {
        DataPermissionInterceptorJSQLParser interceptor =
            new DataPermissionInterceptorJSQLParser(rules);

        assertNotNull(interceptor, "拦截器应正确初始化");
    }

    @Test
    @DisplayName("空规则列表应正常处理")
    void testEmptyRulesList() {
        List<DataPermissionRule> emptyRules = new ArrayList<>();
        DataPermissionInterceptorJSQLParser interceptor =
            new DataPermissionInterceptorJSQLParser(emptyRules);

        assertNotNull(interceptor, "空规则列表应正常处理");
    }

    // ==================== 辅助方法 ====================

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
            return Set.of("sys_user", "sys_dept", "sys_position");
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
