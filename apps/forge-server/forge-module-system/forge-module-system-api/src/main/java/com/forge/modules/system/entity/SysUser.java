package com.forge.modules.system.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.forge.framework.mybatis.annotation.EncryptField;
import com.forge.framework.mybatis.handler.EncryptTypeHandler;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户实体
 *
 * @author standadmin
 */
@Data
@TableName("sys_user")
public class SysUser {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 用户名
     */
    private String username;

    /**
     * 昵称
     */
    private String nickname;

    /**
     * 密码
     */
    private String password;

    /**
     * 手机号（加密存储）
     */
    @EncryptField
    @TableField(typeHandler = EncryptTypeHandler.class)
    private String phone;

    /**
     * 邮箱（加密存储）
     */
    @EncryptField
    @TableField(typeHandler = EncryptTypeHandler.class)
    private String email;

    /**
     * 头像
     */
    private String avatar;

    /**
     * 部门ID
     */
    private Long deptId;

    /**
     * 租户ID
     */
    private Long tenantId;

    /**
     * 岗位ID
     */
    private Long positionId;

    /**
     * 账户类型(0:普通用户 1:管理员)
     */
    private Integer accountType;

    /**
     * 状态(0:禁用 1:启用)
     */
    private Integer status;

    /**
     * 最后登录时间
     */
    private LocalDateTime lastLoginTime;

    /**
     * 最后登录IP
     */
    private String lastLoginIp;

    @Schema(description = "密码最后修改时间")
    @TableField("password_update_time")
    private LocalDateTime passwordUpdateTime;

    @Schema(description = "是否首次登录需强制改密(0:否 1:是)")
    @TableField("first_login")
    private Integer firstLogin;

    @Schema(description = "连续登录失败次数")
    @TableField("password_error_count")
    private Integer passwordErrorCount;

    @Schema(description = "账号锁定截止时间")
    @TableField("lock_time")
    private LocalDateTime lockTime;

    @Schema(description = "手机号后4位（明文，便于精确查询）")
    @TableField("phone_suffix")
    private String phoneSuffix;

    /**
     * 角色列表（非数据库字段）
     */
    @TableField(exist = false)
    private List<SysRole> roles;

    /**
     * 角色ID列表（非数据库字段）
     */
    @TableField(exist = false)
    private List<Long> roleIds;

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
