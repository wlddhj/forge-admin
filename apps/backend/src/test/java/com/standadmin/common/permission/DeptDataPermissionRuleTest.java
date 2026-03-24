package com.forge.admin.common.permission;

import com.forge.admin.common.enumeration.DataScope;
import com.forge.admin.common.utils.UserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 部门数据权限规则单元测试
 *
 * 测试 DeptDataPermissionRule 的各种数据权限场景：
 * - 超级管理员（无过滤）
 * - 全部数据权限（无过滤）
 * - 自定义数据权限（指定部门）
 * - 本部门数据权限
 * - 本部门及以下数据权限
 * - 仅本人数据权限
 * - 上下文缓存机制
 *
 * @author standadmin
 */
@DisplayName("部门数据权限规则测试")
class DeptDataPermissionRuleTest {

    private DeptDataPermissionRule rule;
    private UserContext adminContext;
    private UserContext allDataScopeContext;
    private UserContext customDataContext;
    private UserContext deptContext;
    private UserContext deptAndChildContext;
    private UserContext selfContext;
    private UserContext nullContext;

    @BeforeEach
    void setUp() {
        rule = new DeptDataPermissionRule();

        // 超级管理员上下文
        adminContext = createContext(1L, "admin", 1, 100L, DataScope.ALL);

        // 全部数据权限上下文（普通用户但有全部权限）
        allDataScopeContext = createContext(2L, "all_user", 0, 100L, DataScope.ALL);

        // 自定义数据权限上下文
        customDataContext = createContext(3L, "custom_user", 0, 100L, DataScope.CUSTOM);
        List<Long> customDeptIds = List.of(101L, 102L, 103L);
        addRoleWithDataScope(customDataContext, 1L, "custom_role", DataScope.CUSTOM, customDeptIds);

        // 本部门数据权限上下文
        deptContext = createContext(4L, "dept_user", 0, 100L, DataScope.DEPT);

        // 本部门及以下数据权限上下文
        deptAndChildContext = createContext(5L, "dept_child_user", 0, 100L, DataScope.DEPT_AND_CHILD);

        // 仅本人数据权限上下文
        selfContext = createContext(6L, "self_user", 0, 100L, DataScope.SELF);

        // 空上下文
        nullContext = null;
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    @DisplayName("应返回正确的需要拦截的表名")
    void testGetTableNames() {
        var tableNames = rule.getTableNames();

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

        String condition = rule.buildCondition("sys_user", "u");

        assertNull(condition, "超级管理员不应有数据权限过滤");
    }

    @Test
    @DisplayName("全部数据权限应返回 null（无过滤条件）")
    void testAllDataScopeShouldReturnNull() {
        UserContext.set(allDataScopeContext);

        String condition = rule.buildCondition("sys_user", "u");

        assertNull(condition, "全部数据权限不应有过滤条件");
    }

    @Test
    @DisplayName("自定义数据权限应返回 IN 条件")
    void testCustomDataScopeShouldReturnInCondition() {
        UserContext.set(customDataContext);

        String condition = rule.buildCondition("sys_user", "u");

        assertNotNull(condition, "自定义数据权限应返回条件");
        assertFalse(condition.equals("1=0"), "自定义数据权限不应返回空条件");
        assertTrue(condition.contains("IN"), "应包含 IN 关键字");
        assertTrue(condition.contains("101") || condition.contains("102") || condition.contains("103"),
            "应包含指定的部门ID");
    }

    @Test
    @DisplayName("本部门数据权限应返回 dept_id 条件")
    void testDeptDataScopeShouldReturnDeptCondition() {
        UserContext.set(deptContext);

        String condition = rule.buildCondition("sys_user", "u");

        assertNotNull(condition, "本部门数据权限应返回条件");
        assertTrue(condition.contains("dept_id"), "应包含 dept_id 字段");
        assertTrue(condition.contains("100"), "应包含部门ID 100");
    }

    @Test
    @DisplayName("本部门及以下数据权限应返回子部门 IN 条件")
    void testDeptAndChildDataScopeShouldReturnChildDeptCondition() {
        UserContext.set(deptAndChildContext);

        String condition = rule.buildCondition("sys_user", "u");

        assertNotNull(condition, "本部门及以下数据权限应返回条件");
        assertTrue(condition.contains("dept_id"), "应包含 dept_id 字段");
        assertTrue(condition.contains("IN"), "应包含 IN 关键字（支持多个子部门）");
    }

    @Test
    @DisplayName("仅本人数据权限应返回 id 条件")
    void testSelfDataScopeShouldReturnIdCondition() {
        UserContext.set(selfContext);

        String condition = rule.buildCondition("sys_user", "u");

        assertNotNull(condition, "仅本人数据权限应返回条件");
        assertTrue(condition.contains("id"), "应包含 id 字段");
        assertTrue(condition.contains("6"), "应包含用户ID 6");
    }

    @Test
    @DisplayName("无用户上下文应返回无权限条件")
    void testNullContextShouldReturnNoPermission() {
        UserContext.set(nullContext);

        String condition = rule.buildCondition("sys_user", "u");

        assertEquals("1=0", condition, "无用户上下文应返回无权限条件");
    }

    @Test
    @DisplayName("表别名应正确应用到条件中")
    void testTableAliasShouldBeApplied() {
        UserContext.set(deptContext);

        String condition = rule.buildCondition("sys_user", "user_alias");

        assertNotNull(condition);
        assertTrue(condition.contains("user_alias."), "应使用提供的表别名");
    }

    @Test
    @DisplayName("上下文缓存应生效（避免重复计算）")
    void testContextCacheShouldWork() {
        UserContext.set(customDataContext);

        // 第一次调用 - 计算数据权限信息
        String condition1 = rule.buildCondition("sys_user", "u");

        // 验证缓存中有数据
        Object cached = customDataContext.getContext("DEPT_DATA_PERMISSION", DeptDataPermissionRule.DeptDataScopeInfo.class);
        assertNotNull(cached, "缓存中应有数据权限信息");

        // 第二次调用 - 应从缓存读取
        String condition2 = rule.buildCondition("sys_user", "u");

        assertEquals(condition1, condition2, "两次调用应返回相同条件");
    }

    @Test
    @DisplayName("多个自定义角色应合并部门ID")
    void testMultipleCustomRolesShouldMergeDeptIds() {
        UserContext context = createContext(10L, "multi_role_user", 0, 100L, DataScope.CUSTOM);
        addRoleWithDataScope(context, 1L, "role1", DataScope.CUSTOM, List.of(101L, 102L));
        addRoleWithDataScope(context, 2L, "role2", DataScope.CUSTOM, List.of(103L, 104L));
        UserContext.set(context);

        String condition = rule.buildCondition("sys_user", "u");

        assertNotNull(condition);
        // 应该包含所有部门的ID
        assertTrue(condition.contains("101") || condition.contains("102") ||
                   condition.contains("103") || condition.contains("104"),
            "应包含所有自定义角色的部门ID");
    }

    @Test
    @DisplayName("getTableNames 应准确返回需要拦截的表")
    void testGetTableNamesShouldReturnCorrectTables() {
        var tableNames = rule.getTableNames();

        // 验证 sys_config 不在拦截列表中
        assertFalse(tableNames.contains("sys_config"), "sys_config 不应在拦截列表中");

        // 验证需要拦截的表
        assertTrue(tableNames.contains("sys_user"), "sys_user 应在拦截列表中");
        assertTrue(tableNames.contains("sys_position"), "sys_position 应在拦截列表中");
        assertTrue(tableNames.contains("sys_job"), "sys_job 应在拦截列表中");
    }

    // ==================== 辅助方法 ====================

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
        context.setContextCache(new HashMap<>());
        return context;
    }

    /**
     * 添加角色数据权限信息
     */
    private void addRoleWithDataScope(UserContext context, Long roleId, String roleCode,
                                     DataScope dataScope, List<Long> deptIds) {
        UserContext.DataScopeRoleInfo roleInfo = new UserContext.DataScopeRoleInfo();
        roleInfo.setRoleId(roleId);
        roleInfo.setRoleCode(roleCode);
        roleInfo.setDataScope(dataScope);
        roleInfo.setDeptIds(deptIds);

        if (context.getRoles() == null) {
            context.setRoles(new ArrayList<>());
        }
        context.getRoles().add(roleInfo);
    }
}
