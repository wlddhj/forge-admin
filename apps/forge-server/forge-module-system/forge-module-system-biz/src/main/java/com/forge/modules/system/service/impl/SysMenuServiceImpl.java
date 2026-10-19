package com.forge.modules.system.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.forge.common.exception.BusinessException;
import com.forge.common.response.ResultCode;
import com.forge.common.utils.UserContext;
import com.forge.modules.system.api.tenant.TenantApi;
import com.forge.modules.system.dto.menu.MenuRequest;
import com.forge.modules.system.dto.menu.MenuResponse;
import com.forge.modules.system.dto.menu.MenuTreeResponse;
import com.forge.modules.system.entity.SysMenu;
import com.forge.modules.system.mapper.SysMenuMapper;
import com.forge.modules.system.service.SysMenuService;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    private final CacheManager cacheManager;
    private final TenantApi tenantApi;

    @Override
    public void refreshMenuCache() {
        Cache cache = cacheManager.getCache("menu");
        if (cache != null) {
            cache.clear();
        }
    }

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
    public List<MenuTreeResponse> getMenuTree() {
        List<SysMenu> menus = lambdaQuery()
                .eq(SysMenu::getStatus, 1)
                .orderByAsc(SysMenu::getSortOrder)
                .list();
        UserContext userContext = UserContext.get();
        if (userContext == null || !userContext.isPlatformAdmin()) {
            Set<Long> allowedMenuIds = getPackageAllowedMenuIds(
                    userContext == null ? null : userContext.getTenantId());
            if (allowedMenuIds.isEmpty()) {
                return new ArrayList<>();
            }
            menus = menus.stream().filter(menu -> allowedMenuIds.contains(menu.getId())).collect(Collectors.toList());
        }
        return buildMenuTree(menus, 0L, false);
    }

    @Override
    @Cacheable(value = "menu", key = "'user:' + #userId", unless = "#result == null || #result.isEmpty()")
    public List<MenuTreeResponse> getUserMenuTree(Long userId) {
        List<SysMenu> menus = sysMenuMapper.selectMenusByUserId(userId);
        UserContext userContext = UserContext.get();
        if (userContext == null || !userContext.isPlatformAdmin()) {
            Set<Long> allowedMenuIds = getPackageAllowedMenuIds(
                    userContext == null ? null : userContext.getTenantId());
            if (allowedMenuIds.isEmpty()) {
                return new ArrayList<>();
            }
            menus = menus.stream().filter(menu -> allowedMenuIds.contains(menu.getId())).collect(Collectors.toList());
        }
        return buildMenuTree(menus, 0L, true);
    }

    @Override
    public Set<Long> getPackageAllowedMenuIds(Long tenantId) {
        List<Long> packageMenuIds = tenantApi.getTenantPackageMenuIds(tenantId);
        if (packageMenuIds == null || packageMenuIds.isEmpty()) {
            return new HashSet<>();
        }

        // 套餐中允许的子菜单需要祖先目录才能构成有效树；祖先是否最终可见由调用方与其他菜单集合的交集决定。
        Set<Long> allowedMenuIds = new HashSet<>(packageMenuIds);
        Map<Long, SysMenu> allMenusById = sysMenuMapper.selectList(null).stream()
                .collect(Collectors.toMap(SysMenu::getId, menu -> menu, (first, ignored) -> first));
        for (Long menuId : packageMenuIds) {
            SysMenu menu = allMenusById.get(menuId);
            Set<Long> visited = new HashSet<>();
            while (menu != null && menu.getParentId() != null && menu.getParentId() != 0L) {
                Long parentId = menu.getParentId();
                if (!visited.add(parentId)) {
                    break;
                }
                allowedMenuIds.add(parentId);
                menu = allMenusById.get(parentId);
            }
        }
        return allowedMenuIds;
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
        resolveExternalFlag(menu);
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
        resolveExternalFlag(menu);
        updateById(menu);
    }

    /**
     * isExternal 以 routePath 前缀为准重算，不信任入参，保证与路由地址规则一致
     */
    private void resolveExternalFlag(SysMenu menu) {
        String routePath = menu.getRoutePath();
        boolean external = routePath != null
                && (routePath.startsWith("http://") || routePath.startsWith("https://"));
        menu.setIsExternal(external ? 1 : 0);
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

    private List<MenuTreeResponse> buildMenuTree(List<SysMenu> menus, Long parentId, boolean excludeButtons) {
        List<MenuTreeResponse> tree = new ArrayList<>();

        Map<Long, List<SysMenu>> menuMap = menus.stream()
                .filter(m -> !excludeButtons || m.getMenuType() != 2)
                .collect(Collectors.groupingBy(SysMenu::getParentId));

        List<SysMenu> parentMenus = menuMap.getOrDefault(parentId, new ArrayList<>());
        for (SysMenu menu : parentMenus) {
            MenuTreeResponse node = convertToTreeResponse(menu);
            node.setChildren(buildMenuTree(menus, menu.getId(), excludeButtons));
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
