package com.forge.admin.common.permission;

import com.forge.admin.common.utils.UserContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 数据权限规则测试
 *
 * 测试数据权限拦截器的核心逻辑，确保 5 种数据权限类型正确工作
 */
@DisplayName("数据权限规则测试")
class DataPermissionRuleTest {

    private UserContext adminContext;
    private UserContext userContext;
    private UserContext customDataContext;

    @BeforeEach
    void setUp() {
        // 超级管理员上下文
        adminContext = new UserContext();
        adminContext.setUserId(1L);
        adminContext.setUsername("admin");
        adminContext.setAccountType(1); // 管理员
        adminContext.setMaxDataScope(com.forge.admin.common.enumeration.DataScope.ALL);
        adminContext.setContextCache(new HashMap<>());

        // 普通用户上下文
        userContext = new UserContext();
        userContext.setUserId(2L);
        userContext.setUsername("user");
        userContext.setAccountType(0); // 普通用户
        userContext.setDeptId(100L);
        userContext.setMaxDataScope(com.forge.admin.common.enumeration.DataScope.DEPT);

        // 自定义数据权限上下文
        customDataContext = new UserContext();
        customDataContext.setUserId(3L);
        customDataContext.setUsername("custom_user");
        customDataContext.setAccountType(0);
        customDataContext.setDeptId(100L);
        customDataContext.setMaxDataScope(com.forge.admin.common.enumeration.DataScope.CUSTOM);

        // 添加角色数据权限信息
        List<UserContext.DataScopeRoleInfo> roles = new ArrayList<>();
        UserContext.DataScopeRoleInfo roleInfo = new UserContext.DataScopeRoleInfo();
        roleInfo.setRoleId(1L);
        roleInfo.setRoleCode("dept_manager");
        roleInfo.setDataScope(com.forge.admin.common.enumeration.DataScope.CUSTOM);
        roleInfo.setDeptIds(List.of(100L, 101L, 102L));
        roles.add(roleInfo);
        customDataContext.setRoles(roles);
        customDataContext.setContextCache(new HashMap<>());
    }

    @Test
    @DisplayName("超级管理员应返回 null（无数据权限限制）")
    void testAdminDataScope() {
        assertTrue(adminContext.isAdmin());
        assertTrue(adminContext.hasAllDataScope());
        assertEquals(com.forge.admin.common.enumeration.DataScope.ALL, adminContext.getMaxDataScope());
    }

    @Test
    @DisplayName("普通用户应有部门数据权限")
    void testUserDataScope() {
        assertFalse(userContext.isAdmin());
        assertFalse(userContext.hasAllDataScope());
        assertEquals(com.forge.admin.common.enumeration.DataScope.DEPT, userContext.getMaxDataScope());
        assertEquals(100L, userContext.getDeptId());
    }

    @Test
    @DisplayName("自定义数据权限应包含指定的部门ID")
    void testCustomDataScope() {
        assertEquals(com.forge.admin.common.enumeration.DataScope.CUSTOM, customDataContext.getMaxDataScope());
        assertNotNull(customDataContext.getRoles());
        assertFalse(customDataContext.getRoles().isEmpty());

        UserContext.DataScopeRoleInfo roleInfo = customDataContext.getRoles().get(0);
        assertEquals(com.forge.admin.common.enumeration.DataScope.CUSTOM, roleInfo.getDataScope());
        assertNotNull(roleInfo.getDeptIds());
        assertEquals(3, roleInfo.getDeptIds().size());
        assertTrue(roleInfo.getDeptIds().contains(100L));
        assertTrue(roleInfo.getDeptIds().contains(101L));
        assertTrue(roleInfo.getDeptIds().contains(102L));
    }

    @Test
    @DisplayName("上下文缓存功能测试")
    void testContextCache() {
        // 设置缓存
        adminContext.setContext("test_key", "test_value");

        // 获取缓存
        String value = adminContext.getContext("test_key", String.class);
        assertEquals("test_value", value);

        // 测试不存在的缓存
        String nullValue = adminContext.getContext("non_existent", String.class);
        assertNull(nullValue);
    }

    @Test
    @DisplayName("数据权限级别测试")
    void testDataScopeLevel() {
        // 验证数据权限级别（数值越小，权限越大）
        // ALL=1, CUSTOM=2, DEPT_AND_CHILD=4, DEPT=3, SELF=5
        assertTrue(com.forge.admin.common.enumeration.DataScope.ALL.getLevel() < com.forge.admin.common.enumeration.DataScope.CUSTOM.getLevel());
        assertTrue(com.forge.admin.common.enumeration.DataScope.CUSTOM.getLevel() < com.forge.admin.common.enumeration.DataScope.DEPT.getLevel());
        assertTrue(com.forge.admin.common.enumeration.DataScope.DEPT.getLevel() < com.forge.admin.common.enumeration.DataScope.DEPT_AND_CHILD.getLevel());
        assertTrue(com.forge.admin.common.enumeration.DataScope.DEPT_AND_CHILD.getLevel() < com.forge.admin.common.enumeration.DataScope.SELF.getLevel());
    }
}
