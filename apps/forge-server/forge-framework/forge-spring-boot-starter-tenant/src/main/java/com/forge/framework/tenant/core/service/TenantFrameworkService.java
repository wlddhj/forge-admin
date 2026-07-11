package com.forge.framework.tenant.core.service;

/**
 * 租户框架服务接口
 */
public interface TenantFrameworkService {

    /**
     * 校验租户合法（存在 + 启用 + 未过期）
     *
     * @param tenantId 租户ID
     */
    void validTenant(Long tenantId);
}
