package com.forge.modules.system.dto.menu;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class MenuResponse {
    private Long id;
    private String menuName;
    private Long parentId;
    private String routePath;
    private String componentPath;
    private String redirectPath;
    private String icon;
    private Integer sortOrder;
    private Integer menuType;
    private String permission;
    private Integer status;
    private Integer visible;
    private Integer isExternal;
    private Integer isCached;
    private LocalDateTime createTime;

    /**
     * 归属插件编码（null=底座菜单；标记后该菜单受插件启停过滤并可能被插件注册同步接管）
     */
    private String pluginCode;

    /**
     * 插件菜单节点键（同一插件内唯一，插件菜单 diff 的幂等键）
     */
    private String pluginMenuKey;
}
