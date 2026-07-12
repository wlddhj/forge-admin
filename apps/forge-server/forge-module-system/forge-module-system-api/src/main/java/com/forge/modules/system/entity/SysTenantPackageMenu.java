package com.forge.modules.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 租户套餐-菜单关联实体
 *
 * @author standadmin
 */
@Data
@TableName("sys_tenant_package_menu")
public class SysTenantPackageMenu {

    /**
     * 套餐ID
     */
    private Long tenantPackageId;

    /**
     * 菜单ID
     */
    private Long menuId;
}
