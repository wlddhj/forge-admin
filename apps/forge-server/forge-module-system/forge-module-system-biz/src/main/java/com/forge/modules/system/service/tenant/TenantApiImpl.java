package com.forge.modules.system.service.tenant;

import com.forge.common.exception.BusinessException;
import com.forge.common.response.ResultCode;
import com.forge.framework.tenant.core.context.TenantContextHolder;
import com.forge.modules.system.api.tenant.TenantApi;
import com.forge.modules.system.entity.SysTenant;
import com.forge.modules.system.service.SysTenantPackageService;
import com.forge.modules.system.service.SysTenantService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

/**
 * 租户 RPC 接口实现（system-biz 提供，供其他模块调用）
 *
 * @author standadmin
 */
@Service
@RequiredArgsConstructor
public class TenantApiImpl implements TenantApi {

    private final SysTenantService tenantService;
    private final SysTenantPackageService packageService;

    @Override
    public void validTenant(Long tenantId) {
        if (tenantId == null) {
            throw new BusinessException(ResultCode.TENANT_NOT_EXISTS);
        }
        boolean prevIgnore = TenantContextHolder.isIgnore();
        try {
            TenantContextHolder.setIgnore(true);
            SysTenant tenant = tenantService.getById(tenantId);
            if (tenant == null) {
                throw new BusinessException(ResultCode.TENANT_NOT_EXISTS);
            }
            if (tenant.getStatus() == null || tenant.getStatus() != 1) {
                throw new BusinessException(ResultCode.TENANT_DISABLED);
            }
            LocalDateTime expireTime = tenant.getExpireTime();
            if (expireTime != null && expireTime.isBefore(LocalDateTime.now())) {
                throw new BusinessException(ResultCode.TENANT_EXPIRED);
            }
        } finally {
            TenantContextHolder.setIgnore(prevIgnore);
        }
    }

    @Override
    public String getTenantName(Long tenantId) {
        if (tenantId == null) {
            return null;
        }
        boolean prevIgnore = TenantContextHolder.isIgnore();
        try {
            TenantContextHolder.setIgnore(true);
            SysTenant tenant = tenantService.getById(tenantId);
            return tenant != null ? tenant.getName() : null;
        } finally {
            TenantContextHolder.setIgnore(prevIgnore);
        }
    }

    @Override
    public List<Long> getTenantPackageMenuIds(Long tenantId) {
        if (tenantId == null) {
            return Collections.emptyList();
        }
        boolean prevIgnore = TenantContextHolder.isIgnore();
        try {
            TenantContextHolder.setIgnore(true);
            SysTenant tenant = tenantService.getById(tenantId);
            if (tenant == null || tenant.getPackageId() == null) {
                return Collections.emptyList();
            }
            return packageService.getMenuIdsByPackageId(tenant.getPackageId());
        } finally {
            TenantContextHolder.setIgnore(prevIgnore);
        }
    }
}
