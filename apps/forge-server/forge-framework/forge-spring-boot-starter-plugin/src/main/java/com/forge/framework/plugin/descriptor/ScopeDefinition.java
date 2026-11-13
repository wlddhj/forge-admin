package com.forge.framework.plugin.descriptor;

/**
 * 插件数据 scope 声明（勾选式授予的选项来源）。
 *
 * <p>管理端授予界面只展示插件声明过的 scope：勾选=授予、取消=回收；
 * License 载荷内置 scope 必须是声明集的子集，超出则整个文件拒绝。
 *
 * @author forge-admin
 */
public record ScopeDefinition(
        /** scope 码，格式 资源:动作（如 user:write / dept:write） */
        String scope,
        /** 中文名（勾选框标签） */
        String name,
        /** 说明文案（勾选框辅助提示） */
        String description,
        /** 读类默认项（如 user:read）：默认放行、界面不可取消 */
        boolean grantedByDefault) {

    public static ScopeDefinition readOnly(String scope, String name, String description) {
        return new ScopeDefinition(scope, name, description, true);
    }

    public static ScopeDefinition writable(String scope, String name, String description) {
        return new ScopeDefinition(scope, name, description, false);
    }
}
