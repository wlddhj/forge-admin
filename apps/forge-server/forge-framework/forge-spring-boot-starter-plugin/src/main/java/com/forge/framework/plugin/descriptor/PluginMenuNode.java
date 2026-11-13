package com.forge.framework.plugin.descriptor;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 插件菜单/权限点声明节点。
 *
 * <p>字段语义与 sys_menu 对应列一致：menuType 0目录/1菜单/2按钮；
 * 按钮型节点只带 permission 不带路由。
 *
 * @author forge-admin
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PluginMenuNode {

    /** 节点键：同一插件声明内唯一；根节点 parentKey=null */
    private String menuKey;

    /** 父节点键（同一声明内的 menuKey）；顶级传 null */
    private String parentKey;

    /** 菜单名称 */
    private String menuName;

    /** 0目录 / 1菜单 / 2按钮 */
    private Integer menuType;

    /** 路由路径（目录/菜单） */
    private String routePath;

    /** 前端组件路径（菜单，如 /views/demo/project/index） */
    private String componentPath;

    /** 权限标识（菜单/按钮，如 demo:project:list） */
    private String permission;

    /** 图标名 */
    private String icon;

    /** 排序号 */
    @Builder.Default
    private Integer sortOrder = 0;

    /** 是否显示（默认 1 显示） */
    @Builder.Default
    private Integer visible = 1;

    /** 路由参数/重定向等扩展（暂占位，同步时忽略） */
    private String redirectPath;

    /** 是否外链 */
    @Builder.Default
    private Boolean isExternal = false;

    /** 是否缓存 */
    @Builder.Default
    private Boolean isCached = false;
}
