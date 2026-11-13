package com.forge.modules.plugin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 租户 × 插件启停状态（全局共享表、tenant_id 显式列，不参与租户插件自动注入）。
 *
 * @author forge-admin
 */
@Data
@TableName("sys_plugin_tenant")
public class SysPluginTenant {

    /** 停用原因常量：License 到期由巡检自动停用 */
    public static final String REASON_LICENSE_EXPIRED = "license_expired";

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    private String pluginCode;

    /** 0停用 1启用 */
    private Integer enabled;

    /** 停用原因（手动停用留空 / license_expired） */
    private String disabledReason;

    private LocalDateTime enabledTime;

    private LocalDateTime disabledTime;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    /**
     * 墓碑列：delval 显式写列名 id——uk 含 deleted 列（uk_tenant_plugin）。
     */
    @TableLogic(delval = "id")
    private Long deleted;
}
