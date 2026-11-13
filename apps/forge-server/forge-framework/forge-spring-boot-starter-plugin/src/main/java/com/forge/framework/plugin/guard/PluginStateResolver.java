package com.forge.framework.plugin.guard;

import java.util.Set;

/**
 * 插件状态解析 SPI——契约层只定义问询接口，实现由插件管理业务模块提供
 * （查 sys_plugin / sys_plugin_tenant + Redis 缓存）。
 *
 * <p>底座未装配插件管理模块时无实现 Bean，调用方（切面/菜单过滤）按
 * 「未装配」语义放行或不过滤，保证契约层可独立裁剪。
 *
 * @author forge-admin
 */
public interface PluginStateResolver {

    /**
     * 指定租户能否访问指定插件：已注册、未被平台总闸禁用且租户已启用。
     *
     * @param tenantId  租户编号
     * @param pluginCode 插件编码
     * @return true=放行；false=拒绝（未注册/总闸禁用/未启用/License 已失效停用）
     */
    boolean isEnabled(Long tenantId, String pluginCode);

    /**
     * 租户当前启用的插件编码集（菜单/权限码过滤用）。
     *
     * @return 启用编码集（可能为空集）；插件体系未装配时实现方不应被调用（无 Bean）
     */
    Set<String> getEnabledPluginCodes(Long tenantId);
}
