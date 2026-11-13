package com.forge.modules.plugin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理端授予的插件数据 scope（全局共享表、tenant_id 显式列）。
 *
 * <p>只存管理端勾选授予的 scope；License 内置 scope 不落此表，
 * 校验时动态合并（有效 scope = 本表 ∪ 当前有效 License 载荷）。
 *
 * @author forge-admin
 */
@Data
@TableName("sys_plugin_scope")
public class SysPluginScope {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    private String pluginCode;

    /** scope 码（必须 ∈ 插件 declaredScopes 声明集，如 user:write） */
    private String scope;

    private Long grantBy;

    private LocalDateTime grantTime;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    /**
     * 墓碑列：delval 显式写列名 id——uk 含 deleted 列（uk_scope）。
     */
    @TableLogic(delval = "id")
    private Long deleted;
}
