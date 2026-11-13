package com.forge.modules.plugin.registry;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.forge.common.exception.BusinessException;
import com.forge.common.response.ResultCode;
import com.forge.framework.plugin.descriptor.PluginDescriptor;
import com.forge.framework.plugin.descriptor.PluginMenuNode;
import com.forge.modules.system.entity.SysMenu;
import com.forge.modules.system.entity.SysRole;
import com.forge.modules.system.entity.SysRoleMenu;
import com.forge.modules.system.mapper.SysMenuMapper;
import com.forge.modules.system.mapper.SysRoleMapper;
import com.forge.modules.system.mapper.SysRoleMenuMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 插件菜单 diff 同步：按 pluginMenuKey 幂等导入/更新/删除 sys_menu，
 * 保留菜单 id 不断角色关联；删除节点同步清理 sys_role_menu 引用。
 *
 * <p>单插件失败不影响其他插件注册（调用方 try-catch per descriptor）。
 *
 * @author forge-admin
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PluginMenuSyncService {

    private final SysMenuMapper sysMenuMapper;

    private final SysRoleMenuMapper sysRoleMenuMapper;

    private final SysRoleMapper sysRoleMapper;

    /** 内置超管角色编码：插件菜单导入后自动补授权，保证全新安装后超管即可见可用 */
    private static final String SUPER_ADMIN_ROLE_CODE = "SUPER_ADMIN";

    /**
     * 同步插件声明菜单到 sys_menu（幂等，可重复执行）。
     *
     * @throws BusinessException MENU_SYNC_FAILED 场景外的声明校验错误（key 重复/父键缺失/成环）
     */
    @Transactional(rollbackFor = Exception.class)
    public void syncMenus(PluginDescriptor descriptor) {
        String pluginCode = descriptor.pluginCode();
        List<PluginMenuNode> nodes = descriptor.menus();
        if (nodes == null || nodes.isEmpty()) {
            // 声明为空=清空该插件全部存量菜单（描述符删除菜单的合法途径）
            removeOrphanMenus(pluginCode, Set.of());
            return;
        }

        LinkedHashSet<PluginMenuNode> ordered = validateAndOrder(pluginCode, nodes);

        List<SysMenu> existing = sysMenuMapper.selectList(Wrappers.<SysMenu>lambdaQuery()
                .eq(SysMenu::getPluginCode, pluginCode));
        Map<String, SysMenu> existingByKey = existing.stream()
                .collect(Collectors.toMap(SysMenu::getPluginMenuKey, m -> m, (first, ignored) -> first));

        // 1) 删除：存量 key 不在目标声明集
        Set<String> targetKeys = ordered.stream().map(PluginMenuNode::getMenuKey).collect(Collectors.toSet());
        removeOrphanMenus(pluginCode, targetKeys);

        // 2) 拓扑序插入/更新（父先子后，父 id 先解析）
        Map<String, Long> resolvedIds = new HashMap<>();
        for (PluginMenuNode node : ordered) {
            Long parentId = node.getParentKey() == null ? 0L : resolvedIds.get(node.getParentKey());
            SysMenu current = existingByKey.get(node.getMenuKey());
            if (current == null) {
                SysMenu menu = buildMenu(pluginCode, node, parentId);
                sysMenuMapper.insert(menu);
                resolvedIds.put(node.getMenuKey(), menu.getId());
                grantSuperAdmin(menu.getId());
            } else {
                SysMenu patch = buildMenu(pluginCode, node, parentId);
                patch.setId(current.getId());
                sysMenuMapper.updateById(patch);
                resolvedIds.put(node.getMenuKey(), current.getId());
            }
        }
        log.info("插件[{}]菜单同步完成：声明 {} 节点（新增 {}，更新 {}，删除 {}）",
                pluginCode, ordered.size(),
                ordered.size() - (int) ordered.stream().filter(n -> existingByKey.containsKey(n.getMenuKey())).count(),
                (int) ordered.stream().filter(n -> existingByKey.containsKey(n.getMenuKey())).count(),
                existingByKey.keySet().stream().filter(k -> !targetKeys.contains(k)).count());
    }

    /** 声明校验 + 拓扑排序：menuKey 唯一非空、parentKey 可解析、无环 */
    private LinkedHashSet<PluginMenuNode> validateAndOrder(String pluginCode, List<PluginMenuNode> nodes) {
        Map<String, PluginMenuNode> byKey = new HashMap<>();
        for (PluginMenuNode node : nodes) {
            if (node.getMenuKey() == null || node.getMenuKey().isBlank()) {
                throw error(pluginCode, "存在 menuKey 为空的菜单节点");
            }
            if (byKey.put(node.getMenuKey(), node) != null) {
                throw error(pluginCode, "menuKey 重复: " + node.getMenuKey());
            }
        }
        for (PluginMenuNode node : nodes) {
            String parentKey = node.getParentKey();
            if (parentKey != null && !byKey.containsKey(parentKey)) {
                throw error(pluginCode, "节点 " + node.getMenuKey() + " 的 parentKey=" + parentKey + " 不在声明集内");
            }
        }

        // Kahn 拓扑排序（parentKey=null 视作虚拟根），同时完成环检测
        Map<String, Integer> inDegree = new HashMap<>();
        Map<String, List<String>> children = new HashMap<>();
        for (PluginMenuNode node : nodes) {
            String parent = node.getParentKey() == null ? "" : node.getParentKey();
            inDegree.putIfAbsent(node.getMenuKey(), 0);
            if (!parent.isEmpty()) {
                inDegree.merge(node.getMenuKey(), 1, Integer::sum);
            }
            children.computeIfAbsent(parent, k -> new ArrayList<>()).add(node.getMenuKey());
        }
        Queue<String> queue = new LinkedList<>(children.getOrDefault("", List.of()));
        LinkedHashSet<PluginMenuNode> ordered = new LinkedHashSet<>();
        Set<String> visited = new HashSet<>();
        while (!queue.isEmpty()) {
            String key = queue.poll();
            if (!visited.add(key)) {
                throw error(pluginCode, "menuKey 环或重复: " + key);
            }
            ordered.add(byKey.get(key));
            for (String child : children.getOrDefault(key, List.of())) {
                if (inDegree.merge(child, -1, Integer::sum) == 0) {
                    queue.add(child);
                }
            }
        }
        if (ordered.size() != nodes.size()) {
            throw error(pluginCode, "菜单声明存在父子环");
        }
        return ordered;
    }

    /** 删除 targetKeys 之外的存量插件菜单，连带清理 sys_role_menu 引用（无外键约束，直接删） */
    private void removeOrphanMenus(String pluginCode, Set<String> targetKeys) {
        List<SysMenu> orphans;
        if (targetKeys.isEmpty()) {
            // 声明为空=清空该插件全部菜单（notIn 空集合会生成非法 SQL，走全量查）
            orphans = sysMenuMapper.selectList(Wrappers.<SysMenu>lambdaQuery()
                    .eq(SysMenu::getPluginCode, pluginCode));
        } else {
            orphans = sysMenuMapper.selectList(Wrappers.<SysMenu>lambdaQuery()
                    .eq(SysMenu::getPluginCode, pluginCode)
                    .notIn(SysMenu::getPluginMenuKey, targetKeys));
        }
        for (SysMenu menu : orphans) {
            sysRoleMenuMapper.delete(Wrappers.<SysRoleMenu>lambdaQuery()
                    .eq(SysRoleMenu::getMenuId, menu.getId()));
            sysMenuMapper.deleteById(menu.getId());
        }
        if (!orphans.isEmpty()) {
            log.info("插件[{}]清理存量菜单 {} 个: {}", pluginCode, orphans.size(),
                    orphans.stream().map(SysMenu::getPluginMenuKey).toList());
        }
    }

    /** 新导入菜单自动授予内置超管角色（幂等：已存在则跳过） */
    private void grantSuperAdmin(Long menuId) {
        SysRole superAdmin = sysRoleMapper.selectOne(Wrappers.<SysRole>lambdaQuery()
                .eq(SysRole::getRoleCode, SUPER_ADMIN_ROLE_CODE)
                .eq(SysRole::getIsFixed, 1)
                .last("LIMIT 1"));
        if (superAdmin == null) {
            log.warn("未找到内置超管角色，插件菜单 {} 未自动授权，请在角色管理中手动勾选", menuId);
            return;
        }
        Long exists = sysRoleMenuMapper.selectCount(Wrappers.<SysRoleMenu>lambdaQuery()
                .eq(SysRoleMenu::getRoleId, superAdmin.getId())
                .eq(SysRoleMenu::getMenuId, menuId));
        if (exists == 0) {
            SysRoleMenu roleMenu = new SysRoleMenu();
            roleMenu.setRoleId(superAdmin.getId());
            roleMenu.setMenuId(menuId);
            sysRoleMenuMapper.insert(roleMenu);
        }
    }

    private SysMenu buildMenu(String pluginCode, PluginMenuNode node, Long parentId) {
        SysMenu menu = new SysMenu();
        menu.setPluginCode(pluginCode);
        menu.setPluginMenuKey(node.getMenuKey());
        menu.setParentId(parentId);
        menu.setMenuName(node.getMenuName());
        menu.setMenuType(node.getMenuType());
        menu.setRoutePath(node.getRoutePath());
        menu.setComponentPath(node.getComponentPath());
        menu.setRedirectPath(node.getRedirectPath());
        menu.setIcon(node.getIcon());
        menu.setSortOrder(node.getSortOrder() == null ? 0 : node.getSortOrder());
        menu.setPermission(node.getPermission());
        menu.setStatus(1);
        menu.setVisible(node.getVisible() == null ? 1 : node.getVisible());
        menu.setIsExternal(Boolean.TRUE.equals(node.getIsExternal()) ? 1 : 0);
        menu.setIsCached(Boolean.TRUE.equals(node.getIsCached()) ? 1 : 0);
        return menu;
    }

    private BusinessException error(String pluginCode, String message) {
        return new BusinessException(ResultCode.VALIDATE_FAILED,
                "插件[" + pluginCode + "]菜单声明无效: " + message);
    }
}
