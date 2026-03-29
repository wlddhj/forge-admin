package com.forge.admin.modules.system.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.forge.admin.modules.system.dto.menu.MenuRequest;
import com.forge.admin.modules.system.dto.menu.MenuResponse;
import com.forge.admin.modules.system.dto.menu.MenuTreeResponse;
import com.forge.admin.modules.system.entity.SysMenu;

import java.util.List;

/**
 * 菜单服务接口
 *
 * @author standadmin
 */
public interface SysMenuService extends IService<SysMenu> {

    /**
     * 查询菜单列表
     */
    List<MenuResponse> listMenus(String menuName, Integer status);

    /**
     * 获取菜单树
     */
    List<MenuTreeResponse> getMenuTree();

    /**
     * 获取用户菜单树
     */
    List<MenuTreeResponse> getUserMenuTree(Long userId);

    /**
     * 获取菜单详情
     */
    MenuResponse getMenuDetail(Long id);

    /**
     * 新增菜单
     */
    void addMenu(MenuRequest request);

    /**
     * 更新菜单
     */
    void updateMenu(MenuRequest request);

    /**
     * 删除菜单
     */
    void deleteMenu(Long id);
}
