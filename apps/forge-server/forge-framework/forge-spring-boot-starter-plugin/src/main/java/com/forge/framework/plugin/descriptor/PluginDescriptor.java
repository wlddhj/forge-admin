package com.forge.framework.plugin.descriptor;

import java.util.List;

/**
 * 插件描述符 SPI——编译期模块插件的注册契约。
 *
 * <p>插件模块实现本接口并声明为 Spring Bean，启动时由插件管理模块的注册 Runner
 * 扫描全部实现：元数据 upsert 进 sys_plugin，菜单树按 pluginMenuKey 幂等导入 sys_menu。
 *
 * <p>插件模块自身只需依赖本 starter 与所需业务 starter，实现此接口即完成接入。
 *
 * @author forge-admin
 */
public interface PluginDescriptor {

    /** 插件编码：全局唯一，kebab-case（如 "demo"）；License 与启停记录均以它关联 */
    String pluginCode();

    /** 插件名称（展示用） */
    String name();

    /** 插件描述 */
    default String description() {
        return "";
    }

    /** 提供方（展示用） */
    default String provider() {
        return "";
    }

    /** 插件运行版本：与 Maven 模块版本保持一致（SemVer，如 "1.0.0"） */
    String version();

    /**
     * 插件菜单树与权限点声明。
     *
     * <p>根节点 parentKey 传 null（挂菜单顶级）；非根节点 parentKey 必须指向
     * 同一声明内的其他 menuKey，否则注册失败。导入 sys_menu 时写 plugin_code
     * 与 plugin_menu_key 两列，按 plugin_menuKey 做增删改 diff，保留菜单 id
     * 不断角色关联。
     */
    default List<PluginMenuNode> menus() {
        return List.of();
    }

    /**
     * 插件声明的数据 scope 清单：管理端授予界面只展示此清单（勾选式），
     * License 载荷内置 scope 也必须是其子集。读类 scope（如 user:read）
     * 声明为 {@link ScopeDefinition#grantedByDefault()}=true，界面不可取消。
     */
    default List<ScopeDefinition> declaredScopes() {
        return List.of();
    }

    /**
     * 收费模式默认值声明：true=免费开放（免 License 闸），false=收费授权。
     * 仅在插件首次注册时写入 sys_plugin.charge_mode，此后以管理端设置为准
     * （升级重启不覆盖运营决策）。
     */
    default boolean freeOpen() {
        return false;
    }
}
