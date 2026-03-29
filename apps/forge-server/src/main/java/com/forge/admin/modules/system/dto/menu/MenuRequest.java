package com.forge.admin.modules.system.dto.menu;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class MenuRequest {
    private Long id;
    @NotBlank(message = "菜单名称不能为空")
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
}
