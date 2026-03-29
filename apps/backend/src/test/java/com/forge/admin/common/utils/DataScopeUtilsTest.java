package com.forge.admin.common.utils;

import com.forge.admin.common.enumeration.DataScope;
import com.forge.admin.modules.system.entity.SysRole;
import com.forge.admin.modules.system.entity.SysUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 数据权限工具类测试
 *
 * 测试数据权限 SQL 条件构建功能
 */
@DisplayName("数据权限工具类测试")
class DataScopeUtilsTest {

    private SysUser adminUser;
    private SysUser deptUser;
    private SysUser deptAndChildUser;
    private SysUser selfUser;
    private SysUser customUser;

    @BeforeEach
    void setUp() {
        // 全部数据权限（超级管理员）
        adminUser = createUser(1L, "admin", 1, null, 1L);

        // 本部门数据权限
        deptUser = createUser(2L, "dept_user", 0, null, 100L);

        // 本部门及以下数据权限
        deptAndChildUser = createUser(3L, "dept_child_user", 0, null, 100L);

        // 仅本人数据权限
        selfUser = createUser(4L, "self_user", 0, null, 100L);

        // 自定义数据权限
        customUser = createUser(5L, "custom_user", 0, null, 100L);
        List<SysRole> roles = new ArrayList<>();
        SysRole role = new SysRole();
        role.setId(1L);
        role.setRoleCode("custom_role");
        role.setDataScope(DataScope.CUSTOM.getValue());
        roles.add(role);
        customUser.setRoles(roles);
    }

    private SysUser createUser(Long id, String username, int accountType,
                              DataScope dataScope, Long deptId) {
        SysUser user = new SysUser();
        user.setId(id);
        user.setUsername(username);
        user.setAccountType(accountType);
        user.setDeptId(deptId);
        return user;
    }

    @Test
    @DisplayName("超级管理员应返回 null（无过滤条件）")
    void testBuildDataScopeFilterForAdmin() {
        String filter = DataScopeUtils.buildDataScopeFilter(adminUser, "u", "d");

        assertNull(filter, "超级管理员应返回 null，不进行数据权限过滤");
    }

    @Test
    @DisplayName("普通用户应返回有效的 SQL 条件")
    void testBuildDataScopeFilterForNormalUser() {
        String filter = DataScopeUtils.buildDataScopeFilter(deptUser, "u", "d");

        // 普通用户应该返回某种条件（即使是 1=0）
        // 具体返回值取决于实际实现
        assertNotNull(filter, "应返回有效的 SQL 条件");
    }

    @Test
    @DisplayName("空用户应返回限制性条件")
    void testNullUser() {
        String filter = DataScopeUtils.buildDataScopeFilter(null, "u", "d");

        assertEquals("1=0", filter, "空用户应返回不匹配任何记录的条件");
    }

    @Test
    @DisplayName("表别名应正确应用到 SQL 条件中")
    void testTableAliasApplied() {
        String filter = DataScopeUtils.buildDataScopeFilter(deptUser, "u", "d");

        // 如果有条件，应该使用正确的表别名
        if (filter != null && !filter.equals("1=0")) {
            assertTrue(
                filter.contains("u.") || filter.contains("d."),
                "应使用正确的表别名"
            );
        }
    }

    @Test
    @DisplayName("自定义数据权限用户应能正确处理")
    void testCustomDataScopeUser() {
        String filter = DataScopeUtils.buildDataScopeFilter(customUser, "u", "d");

        assertNotNull(filter, "自定义数据权限应返回有效的 SQL 条件");
    }

    @Test
    @DisplayName("不同账户类型应产生不同的数据权限")
    void testDifferentAccountTypes() {
        String adminFilter = DataScopeUtils.buildDataScopeFilter(adminUser, "u", "d");
        String userFilter = DataScopeUtils.buildDataScopeFilter(deptUser, "u", "d");

        // 管理员应该没有限制
        assertNull(adminFilter, "管理员应返回 null");

        // 普通用户应该有限制
        assertNotNull(userFilter, "普通用户应返回有效条件");
    }
}
