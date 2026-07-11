package com.forge.framework.tenant.core.service;

import com.forge.framework.tenant.core.service.TenantFrameworkService;
import com.forge.modules.system.api.tenant.TenantApi;
import lombok.RequiredArgsConstructor;

/**
 * 租户框架服务实现
 */
@RequiredArgsConstructor
public class TenantFrameworkServiceImpl implements TenantFrameworkService {

    private final TenantApi tenantApi;

    @Override
    public void validTenant(Long tenantId) {
        tenantApi.validTenant(tenantId);
    }
}
