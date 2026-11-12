package com.forge.modules.system.service.tenant;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.forge.common.exception.BusinessException;
import com.forge.common.response.ResultCode;
import com.forge.framework.tenant.core.context.TenantContextHolder;
import com.forge.modules.system.api.tenant.TenantApi;
import com.forge.modules.system.entity.SysTenant;
import com.forge.modules.system.mapper.SysTenantMapper;
import com.forge.modules.system.service.SysTenantPackageService;
import com.forge.modules.system.service.SysTenantService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

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
    private final SysTenantMapper sysTenantMapper;

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

    @Override
    public List<Long> getTenantIds() {
        // 供定时任务按租户遍历：查询启用（status=1）的租户 ID 列表
        // sys_tenant 为跨租户共享表（已在 tenant ignore-tables 清单中），无需 @TenantIgnore
        List<SysTenant> tenants = sysTenantMapper.selectList(new LambdaQueryWrapper<SysTenant>()
                .eq(SysTenant::getStatus, 1));
        return tenants.stream().map(SysTenant::getId).collect(Collectors.toList());
    }
}
