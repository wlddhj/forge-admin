package com.forge.modules.screen.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;

/**
 * 大屏角色授权关系
 */
@Data
@TableName("sys_screen_role")
public class SysScreenRole implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long screenId;

    private Long roleId;
}
