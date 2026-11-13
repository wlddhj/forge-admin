package com.forge.modules.plugin.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 插件数据 scope 视图（勾选式授予界面数据源：声明集全量 + 授予态 + 来源）。
 *
 * @author forge-admin
 */
@Data
@Builder
public class PluginScopeVO {

    /** scope 码 */
    private String scope;

    /** 中文名（勾选框标签） */
    private String name;

    /** 说明 */
    private String description;

    /** 是否已生效（勾选态） */
    private boolean granted;

    /** 读类默认项（不可取消） */
    private boolean grantedByDefault;

    /** 生效来源：default/admin/license（读类默认/管理端勾选/License 内置） */
    private String source;
}
