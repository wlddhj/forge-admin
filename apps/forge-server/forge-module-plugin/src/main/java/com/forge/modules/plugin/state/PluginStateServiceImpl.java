package com.forge.modules.plugin.state;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.forge.framework.plugin.guard.PluginStateResolver;
import com.forge.framework.plugin.menu.PluginMenuFilter;
import com.forge.modules.plugin.entity.SysPlugin;
import com.forge.modules.plugin.entity.SysPluginTenant;
import com.forge.modules.plugin.mapper.SysPluginMapper;
import com.forge.modules.plugin.mapper.SysPluginTenantMapper;
import com.forge.modules.system.entity.SysMenu;
import com.forge.modules.system.mapper.SysMenuMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.core.type.TypeReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 插件状态解析实现（starter SPI 消费方的唯一实现 Bean）：
 * 同时实现 {@link PluginStateResolver}（请求闸）与 {@link PluginMenuFilter}
 * （system 菜单/权限过滤），数据源 sys_plugin × sys_plugin_tenant，Redis 缓存 TTL 300s。
 *
 * @author forge-admin
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PluginStateServiceImpl implements PluginStateResolver, PluginMenuFilter {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final PluginCacheService cacheService;

    private final SysPluginMapper pluginMapper;

    private final SysPluginTenantMapper tenantPluginMapper;

    private final SysMenuMapper sysMenuMapper;

    // ===== PluginStateResolver：@PluginGuard 请求闸 =====

    @Override
    public boolean isEnabled(Long tenantId, String pluginCode) {
        String cached = cacheService.getState(tenantId, pluginCode);
        if (cached != null) {
            return "1".equals(cached);
        }
        boolean enabled = queryEnabled(tenantId, pluginCode);
        cacheService.putState(tenantId, pluginCode, enabled);
        return enabled;
    }

    private boolean queryEnabled(Long tenantId, String pluginCode) {
        Long globalOk = pluginMapper.selectCount(Wrappers.<SysPlugin>lambdaQuery()
                .eq(SysPlugin::getPluginCode, pluginCode)
                .eq(SysPlugin::getGlobalStatus, SysPlugin.GLOBAL_STATUS_ENABLED));
        if (globalOk == 0) {
            return false;
        }
        Long tenantEnabled = tenantPluginMapper.selectCount(Wrappers.<SysPluginTenant>lambdaQuery()
                .eq(SysPluginTenant::getTenantId, tenantId)
                .eq(SysPluginTenant::getPluginCode, pluginCode)
                .eq(SysPluginTenant::getEnabled, 1));
        return tenantEnabled > 0;
    }

    // ===== PluginMenuFilter：system 菜单/权限过滤 =====

    @Override
    public Set<String> getEnabledPluginCodes(Long tenantId) {
        String cached = cacheService.getEnabledCodes(tenantId);
        if (cached != null) {
            try {
                return JSON.readValue(cached, new TypeReference<Set<String>>() {
                });
            } catch (Exception e) {
                // 缓存值损坏（非法 JSON）：弃缓存回源，菜单链路为核心路径不可炸
                log.warn("租户 {} 插件启用集缓存损坏，回源数据库", tenantId, e);
            }
        }
        Set<String> tenantEnabled = tenantPluginMapper.selectList(Wrappers.<SysPluginTenant>lambdaQuery()
                        .eq(SysPluginTenant::getTenantId, tenantId)
                        .eq(SysPluginTenant::getEnabled, 1))
                .stream()
                .map(SysPluginTenant::getPluginCode)
                .collect(Collectors.toSet());
        Set<String> enabled;
        if (tenantEnabled.isEmpty()) {
            enabled = Set.of();
        } else {
            // 全局禁用的插件编码不进可见集（与请求闸口径一致）
            enabled = pluginMapper.selectList(Wrappers.<SysPlugin>lambdaQuery()
                            .select(SysPlugin::getPluginCode)
                            .in(SysPlugin::getPluginCode, tenantEnabled)
                            .eq(SysPlugin::getGlobalStatus, SysPlugin.GLOBAL_STATUS_ENABLED))
                    .stream()
                    .map(SysPlugin::getPluginCode)
                    .collect(Collectors.toSet());
        }
        cacheService.putEnabledCodes(tenantId, JSON.writeValueAsString(enabled));
        return enabled;
    }

    @Override
    public List<Long> getExcludedPluginMenuIds(Long tenantId) {
        Set<String> enabledCodes = getEnabledPluginCodes(tenantId);
        List<Long> excluded;
        if (enabledCodes.isEmpty()) {
            // 无任何启用插件：全部插件菜单均排除
            excluded = sysMenuMapper.selectList(Wrappers.<SysMenu>lambdaQuery()
                            .select(SysMenu::getId)
                            .isNotNull(SysMenu::getPluginCode))
                    .stream().map(SysMenu::getId).toList();
        } else {
            excluded = sysMenuMapper.selectList(Wrappers.<SysMenu>lambdaQuery()
                            .select(SysMenu::getId)
                            .isNotNull(SysMenu::getPluginCode)
                            .notIn(SysMenu::getPluginCode, enabledCodes))
                    .stream().map(SysMenu::getId).toList();
        }
        return excluded;
    }
}
