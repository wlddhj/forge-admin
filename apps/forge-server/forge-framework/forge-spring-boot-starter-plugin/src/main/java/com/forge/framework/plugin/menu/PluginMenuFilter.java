package com.forge.framework.plugin.menu;

import java.util.List;
import java.util.Set;

/**
 * 插件菜单可见性过滤 SPI——供 system 模块在菜单树/权限码查询处接入。
 *
 * <p>实现由插件管理业务模块提供（查 sys_plugin_tenant + sys_menu 的 plugin_code
 * 标记，带缓存）。system 侧注入 {@code Optional<PluginMenuFilter>}：无实现
 * （插件体系未装配）时不过滤，行为与底座无插件时完全一致。
 *
 * @author forge-admin
 */
public interface PluginMenuFilter {

    /**
     * 租户当前启用插件对应的菜单 id 排除集：即「plugin_code 非空 且 该插件
     * 在此租户未启用」的 sys_menu id 列表（权限码 SQL NOT IN 用）。
     *
     * @return 排除 id 列表（可能为空=无排除）
     */
    List<Long> getExcludedPluginMenuIds(Long tenantId);

    /**
     * 租户当前启用的插件编码集（菜单树内存过滤用）。
     *
     * @return 启用编码集（可能为空）
     */
    Set<String> getEnabledPluginCodes(Long tenantId);
}
