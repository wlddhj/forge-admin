package com.forge.modules.system.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.forge.modules.system.dto.menu.MenuRequest;
import com.forge.modules.system.dto.menu.MenuResponse;
import com.forge.modules.system.dto.menu.MenuTreeResponse;
import com.forge.modules.system.entity.SysMenu;

import java.util.List;
import java.util.Set;

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

    /**
     * 刷新菜单缓存（清空 menu 缓存全部条目，下次查询自动重建）
     */
    void refreshMenuCache();

    /**
     * 获取租户套餐允许的菜单 ID 集合（含祖先目录补全）。
     * tenantId 为空、租户不存在或套餐未配置菜单时返回空集合。
     */
    Set<Long> getPackageAllowedMenuIds(Long tenantId);
}
