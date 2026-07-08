package com.forge.modules.screen.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;

/**
 * 大屏角色授权关系
 *
 * <p>复合主键：screenId + roleId
 */
@Data
@TableName("sys_screen_role")
public class SysScreenRole implements Serializable {

    @TableId(type = IdType.INPUT)
    private Long screenId;

    @TableId(type = IdType.INPUT)
    private Long roleId;
}
