package com.forge.modules.plugin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 插件注册表（全局共享表）：启动注册 Runner 维护，记录运行版本与总闸。
 *
 * @author forge-admin
 */
@Data
@TableName("sys_plugin")
public class SysPlugin {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 插件编码（全局唯一，License 与启停记录的关联键） */
    private String pluginCode;

    private String name;

    private String description;

    private String provider;

    /** 运行版本（启动刷新；版本变化记操作日志，无人工确认环节） */
    private String runningVersion;

    /** 1正常 0平台禁用（总闸，停用全部租户访问） */
    private Integer globalStatus;

    /** 1收费授权（License 硬闸） 0免费开放（免闸，仍需租户启用）；描述符默认仅注册首次写入，此后管理端为准 */
    private Integer chargeMode;

    /** 菜单已同步标记（1=已按描述符导入 sys_menu） */
    private Integer menusSynced;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    /**
     * 墓碑列：delval 显式写列名 id——uk 含 deleted 列（uk_plugin），物理语义删除防撞唯一索引。
     */
    @TableLogic(delval = "id")
    private Long deleted;

    public static final int GLOBAL_STATUS_ENABLED = 1;

    public static final int GLOBAL_STATUS_DISABLED = 0;

    public static final int CHARGE_MODE_LICENSE = 1;

    public static final int CHARGE_MODE_FREE = 0;
}
