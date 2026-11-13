package com.forge.modules.system.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 菜单实体
 *
 * @author standadmin
 */
@Data
@TableName("sys_menu")
public class SysMenu {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 菜单名称
     */
    private String menuName;

    /**
     * 父菜单ID
     */
    private Long parentId;

    /**
     * 路由路径
     */
    private String routePath;

    /**
     * 组件路径
     */
    private String componentPath;

    /**
     * 重定向路径
     */
    private String redirectPath;

    /**
     * 图标
     */
    private String icon;

    /**
     * 排序
     */
    private Integer sortOrder;

    /**
     * 类型(0:目录 1:菜单 2:按钮)
     */
    private Integer menuType;

    /**
     * 权限标识
     */
    private String permission;

    /**
     * 状态(0:禁用 1:启用)
     */
    private Integer status;

    /**
     * 是否可见(0:否 1:是)
     */
    private Integer visible;

    /**
     * 是否外链
     */
    private Integer isExternal;

    /**
     * 是否缓存
     */
    private Integer isCached;

    /**
     * 归属插件编码（null=底座菜单；非空=插件菜单，租户可见性受插件启停过滤）
     */
    private String pluginCode;

    /**
     * 插件菜单节点键（同一插件内唯一，插件注册菜单 diff 的幂等键；null=非插件菜单）
     */
    private String pluginMenuKey;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /**
     * 删除标记
     */
    @TableLogic
    private Integer deleted;
}
