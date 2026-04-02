package com.forge.admin.modules.system.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.forge.admin.common.exception.BusinessException;
import com.forge.admin.common.response.ResultCode;
import com.forge.admin.modules.system.dto.menu.MenuRequest;
import com.forge.admin.modules.system.dto.menu.MenuResponse;
import com.forge.admin.modules.system.dto.menu.MenuTreeResponse;
import com.forge.admin.modules.system.entity.SysMenu;
import com.forge.admin.modules.system.mapper.SysMenuMapper;
import com.forge.admin.modules.system.service.SysMenuService;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 菜单服务实现
 *
 * @author standadmin
 */
@Service
@RequiredArgsConstructor
public class SysMenuServiceImpl extends ServiceImpl<SysMenuMapper, SysMenu> implements SysMenuService {

    private final SysMenuMapper sysMenuMapper;

    @Override
    public List<MenuResponse> listMenus(String menuName, Integer status) {
        LambdaQueryWrapper<SysMenu> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StrUtil.isNotBlank(menuName), SysMenu::getMenuName, menuName)
                .eq(status != null, SysMenu::getStatus, status)
                .orderByAsc(SysMenu::getSortOrder);

        List<SysMenu> menus = list(wrapper);
        return menus.stream().map(this::convertToResponse).collect(Collectors.toList());
    }

    @Override
    @Cacheable(value = "menu", key = "'tree:all'", unless = "#result == null || #result.isEmpty()")
    public List<MenuTreeResponse> getMenuTree() {
        List<SysMenu> menus = lambdaQuery()
                .eq(SysMenu::getStatus, 1)
                .orderByAsc(SysMenu::getSortOrder)
                .list();
        return buildMenuTree(menus, 0L);
    }

    @Override
    @Cacheable(value = "menu", key = "'user:' + #userId", unless = "#result == null || #result.isEmpty()")
    public List<MenuTreeResponse> getUserMenuTree(Long userId) {
        List<SysMenu> menus = sysMenuMapper.selectMenusByUserId(userId);
        return buildMenuTree(menus, 0L);
    }

    @Override
    public MenuResponse getMenuDetail(Long id) {
        SysMenu menu = getById(id);
        if (menu == null) {
            throw new BusinessException("菜单不存在");
        }
        return convertToResponse(menu);
    }

    @Override
    @CacheEvict(value = "menu", allEntries = true)
    public void addMenu(MenuRequest request) {
        SysMenu menu = new SysMenu();
        BeanUtil.copyProperties(request, menu);
        save(menu);
    }

    @Override
    @CacheEvict(value = "menu", allEntries = true)
    public void updateMenu(MenuRequest request) {
        SysMenu menu = getById(request.getId());
        if (menu == null) {
            throw new BusinessException("菜单不存在");
        }
        BeanUtil.copyProperties(request, menu);
        updateById(menu);
    }

    @Override
    @CacheEvict(value = "menu", allEntries = true)
    public void deleteMenu(Long id) {
        // 检查是否存在子菜单
        if (sysMenuMapper.hasChildren(id) > 0) {
            throw new BusinessException(ResultCode.MENU_HAS_CHILDREN);
        }
        removeById(id);
    }

    private List<MenuTreeResponse> buildMenuTree(List<SysMenu> menus, Long parentId) {
        List<MenuTreeResponse> tree = new ArrayList<>();

        Map<Long, List<SysMenu>> menuMap = menus.stream()
                .filter(m -> m.getMenuType() != 2) // 过滤掉按钮类型
                .collect(Collectors.groupingBy(SysMenu::getParentId));

        List<SysMenu> parentMenus = menuMap.getOrDefault(parentId, new ArrayList<>());
        for (SysMenu menu : parentMenus) {
            MenuTreeResponse node = convertToTreeResponse(menu);
            node.setChildren(buildMenuTree(menus, menu.getId()));
            tree.add(node);
        }

        return tree;
    }

    private MenuResponse convertToResponse(SysMenu menu) {
        MenuResponse response = new MenuResponse();
        BeanUtil.copyProperties(menu, response);
        return response;
    }

    private MenuTreeResponse convertToTreeResponse(SysMenu menu) {
        MenuTreeResponse response = new MenuTreeResponse();
        BeanUtil.copyProperties(menu, response);
        return response;
    }
}
