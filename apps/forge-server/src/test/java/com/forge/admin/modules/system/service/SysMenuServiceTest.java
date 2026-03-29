package com.forge.admin.modules.system.service;

import com.forge.admin.ForgeAdminApplication;
import com.forge.admin.modules.system.dto.menu.MenuResponse;
import com.forge.admin.modules.system.dto.menu.MenuTreeResponse;
import com.forge.admin.modules.system.entity.SysMenu;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 菜单服务集成测试
 */
@SpringBootTest(classes = ForgeAdminApplication.class)
class SysMenuServiceTest {

    @Autowired
    private SysMenuService sysMenuService;

    @Test
    void testListMenus() {
        List<MenuResponse> menus = sysMenuService.listMenus(null, null);
        assertNotNull(menus);
        assertFalse(menus.isEmpty(), "应有菜单数据");
    }

    @Test
    void testListMenus_withFilter() {
        List<MenuResponse> menus = sysMenuService.listMenus("系统", null);
        assertNotNull(menus);
    }

    @Test
    void testGetMenuTree() {
        List<MenuTreeResponse> tree = sysMenuService.getMenuTree();
        assertNotNull(tree);
        assertFalse(tree.isEmpty(), "菜单树应非空");
        // 顶级菜单的 children 应该是列表
        assertNotNull(tree.get(0).getChildren());
    }

    @Test
    void testGetUserMenuTree() {
        // admin 用户 ID 通常为 1
        List<MenuTreeResponse> tree = sysMenuService.getUserMenuTree(1L);
        assertNotNull(tree);
        assertFalse(tree.isEmpty(), "admin 应有菜单");
    }

    @Test
    void testGetMenuDetail() {
        List<MenuResponse> menus = sysMenuService.listMenus(null, null);
        assertFalse(menus.isEmpty());

        Long menuId = menus.get(0).getId();
        MenuResponse detail = sysMenuService.getMenuDetail(menuId);
        assertNotNull(detail);
        assertNotNull(detail.getMenuName());
    }
}
