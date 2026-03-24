package com.forge.admin.modules.system.dto.menu;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class MenuResponse {
    private Long id;
    private String menuName;
    private String menuCode;
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
}
