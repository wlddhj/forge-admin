package com.forge.admin.modules.system.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.forge.admin.ForgeAdminApplication;
import com.forge.admin.modules.system.dto.user.UserQueryRequest;
import com.forge.admin.modules.system.dto.user.UserResponse;
import com.forge.admin.modules.system.entity.SysUser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 用户服务集成测试
 */
@SpringBootTest(classes = ForgeAdminApplication.class)
class SysUserServiceTest {

    @Autowired
    private SysUserService sysUserService;

    @Test
    void testGetByUsername_admin() {
        SysUser user = sysUserService.getByUsername("admin");
        assertNotNull(user, "admin 用户应存在");
        assertEquals("admin", user.getUsername());
        assertNotNull(user.getId());
    }

    @Test
    void testGetByUsername_notExist() {
        SysUser user = sysUserService.getByUsername("nonexistent_user_xyz");
        assertNull(user, "不存在的用户应返回 null");
    }

    @Test
    void testPageUsers() {
        UserQueryRequest request = new UserQueryRequest();
        request.setPageNum(1);
        request.setPageSize(10);

        Page<UserResponse> page = sysUserService.pageUsers(request);
        assertNotNull(page);
        assertTrue(page.getTotal() > 0, "应有用户数据");
        assertNotNull(page.getRecords());
    }

    @Test
    void testPageUsers_withFilter() {
        UserQueryRequest request = new UserQueryRequest();
        request.setPageNum(1);
        request.setPageSize(10);
        request.setUsername("admin");

        Page<UserResponse> page = sysUserService.pageUsers(request);
        assertNotNull(page);
        assertFalse(page.getRecords().isEmpty());
        // 应包含 admin 用户
        assertTrue(page.getRecords().stream()
                .anyMatch(u -> "admin".equals(u.getUsername())));
    }

    @Test
    void testGetUserDetail_admin() {
        // 先获取 admin 用户 ID
        SysUser user = sysUserService.getByUsername("admin");
        assertNotNull(user);

        UserResponse detail = sysUserService.getUserDetail(user.getId());
        assertNotNull(detail);
        assertEquals("admin", detail.getUsername());
    }

    @Test
    void testGetUserRoleCodes() {
        SysUser user = sysUserService.getByUsername("admin");
        assertNotNull(user);

        java.util.List<String> roleCodes = sysUserService.getUserRoleCodes(user.getId());
        assertNotNull(roleCodes);
        // admin 用户应至少有一个角色
        assertFalse(roleCodes.isEmpty(), "admin 应有角色");
    }

    @Test
    void testGetUserPermissionCodes() {
        SysUser user = sysUserService.getByUsername("admin");
        assertNotNull(user);

        java.util.List<String> permissions = sysUserService.getUserPermissionCodes(user.getId());
        assertNotNull(permissions);
        // admin 应有权限
        assertFalse(permissions.isEmpty(), "admin 应有权限");
    }
}
