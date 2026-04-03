package com.forge.admin.modules.system.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

/**
 * 用户岗位关联实体
 *
 * @author standadmin
 */
@Data
@TableName("sys_user_position")
public class SysUserPosition {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 岗位ID
     */
    private Long positionId;
}
