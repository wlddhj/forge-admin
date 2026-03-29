package com.forge.admin.modules.system.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.forge.admin.ForgeAdminApplication;
import com.forge.admin.modules.system.dto.role.RoleQueryRequest;
import com.forge.admin.modules.system.dto.role.RoleResponse;
import com.forge.admin.modules.system.entity.SysRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 角色服务集成测试
 */
@SpringBootTest(classes = ForgeAdminApplication.class)
class SysRoleServiceTest {

    @Autowired
    private SysRoleService sysRoleService;

    @Test
    void testPageRoles() {
        RoleQueryRequest request = new RoleQueryRequest();
        request.setPageNum(1);
        request.setPageSize(10);

        Page<RoleResponse> page = sysRoleService.pageRoles(request);
        assertNotNull(page);
        assertTrue(page.getTotal() > 0, "应有角色数据");
    }

    @Test
    void testGetAllRoles() {
        List<SysRole> roles = sysRoleService.getAllRoles();
        assertNotNull(roles);
        assertFalse(roles.isEmpty(), "应有启用的角色");
    }

    @Test
    void testGetRoleDetail() {
        // 获取第一个角色
        List<SysRole> roles = sysRoleService.getAllRoles();
        assertFalse(roles.isEmpty());

        Long roleId = roles.get(0).getId();
        RoleResponse detail = sysRoleService.getRoleDetail(roleId);
        assertNotNull(detail);
        assertNotNull(detail.getRoleName());
    }

    @Test
    void testGetRoleMenuIds() {
        List<SysRole> roles = sysRoleService.getAllRoles();
        assertFalse(roles.isEmpty());

        Long roleId = roles.get(0).getId();
        List<Long> menuIds = sysRoleService.getRoleMenuIds(roleId);
        assertNotNull(menuIds);
    }
}
