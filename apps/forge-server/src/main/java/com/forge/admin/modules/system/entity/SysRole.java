package com.forge.admin.modules.system.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.forge.admin.common.enumeration.DataScope;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 角色实体
 *
 * @author standadmin
 */
@Data
@TableName("sys_role")
public class SysRole {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 角色名称
     */
    private String roleName;

    /**
     * 角色编码
     */
    private String roleCode;

    /**
     * 描述
     */
    private String description;

    /**
     * 是否固定(0:否 1:是)
     */
    private Integer isFixed;

    /**
     * 状态(0:禁用 1:启用)
     */
    private Integer status;

    /**
     * 排序
     */
    private Integer sortOrder;

    /**
     * 数据范围（1:全部 2:自定义 3:本部门 4:本部门及以下 5:仅本人）
     */
    private String dataScope;

    /**
     * 数据权限范围（枚举）
     */
    @TableField(exist = false)
    private DataScope dataScopeEnum;

    /**
     * 自定义数据权限-部门ID列表
     */
    @TableField(exist = false)
    private List<Long> deptIds;

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
