package com.forge.modules.system.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

/**
 * 用户角色关联实体
 *
 * @author standadmin
 */
@Data
@TableName("sys_user_role")
public class SysUserRole {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 租户ID（多租户隔离）
     */
    private Long tenantId;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 角色ID
     */
    private Long roleId;
}
