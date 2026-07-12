package com.forge.modules.system.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.forge.common.exception.BusinessException;
import com.forge.common.response.ResultCode;
import com.forge.framework.tenant.core.context.TenantContextHolder;
import com.forge.modules.system.dto.tenant.TenantQueryRequest;
import com.forge.modules.system.dto.tenant.TenantRequest;
import com.forge.modules.system.dto.tenant.TenantResponse;
import com.forge.modules.system.entity.SysTenant;
import com.forge.modules.system.entity.SysTenantPackage;
import com.forge.modules.system.mapper.SysTenantMapper;
import com.forge.modules.system.mapper.SysTenantPackageMapper;
import com.forge.modules.system.service.SysTenantService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 租户服务实现
 *
 * @author standadmin
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysTenantServiceImpl extends ServiceImpl<SysTenantMapper, SysTenant> implements SysTenantService {

    private final SysTenantMapper sysTenantMapper;
    private final SysTenantPackageMapper sysTenantPackageMapper;

    @Override
    public Long getIdByCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        // 登录阶段 TenantContextHolder 尚未设置，使用 ignore 跳过租户拦截器
        return runIgnore(() -> sysTenantMapper.selectIdByCode(code));
    }

    @Override
    public SysTenant getByCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        return runIgnore(() -> sysTenantMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SysTenant>()
                        .eq(SysTenant::getCode, code)));
    }

    @Override
    public SysTenant getById(Long id) {
        if (id == null) {
            return null;
        }
        return runIgnore(() -> sysTenantMapper.selectById(id));
    }

    @Override
    public Page<TenantResponse> pageTenants(TenantQueryRequest request) {
        return runIgnore(() -> {
            Page<SysTenant> page = new Page<>(request.getPageNum(), request.getPageSize());
            LambdaQueryWrapper<SysTenant> wrapper = new LambdaQueryWrapper<>();
            wrapper.like(StrUtil.isNotBlank(request.getName()), SysTenant::getName, request.getName())
                    .like(StrUtil.isNotBlank(request.getCode()), SysTenant::getCode, request.getCode())
                    .eq(request.getStatus() != null, SysTenant::getStatus, request.getStatus())
                    .eq(request.getPackageId() != null, SysTenant::getPackageId, request.getPackageId())
                    .orderByDesc(SysTenant::getCreateTime);

            Page<SysTenant> tenantPage = sysTenantMapper.selectPage(page, wrapper);

            Page<TenantResponse> responsePage = new Page<>();
            responsePage.setCurrent(tenantPage.getCurrent());
            responsePage.setSize(tenantPage.getSize());
            responsePage.setTotal(tenantPage.getTotal());
            responsePage.setRecords(tenantPage.getRecords().stream()
                    .map(this::convertToResponse)
                    .collect(Collectors.toList()));
            return responsePage;
        });
    }

    @Override
    public TenantResponse getTenantDetail(Long id) {
        return runIgnore(() -> {
            SysTenant tenant = sysTenantMapper.selectById(id);
            if (tenant == null) {
                throw new BusinessException(ResultCode.TENANT_NOT_EXISTS);
            }
            return convertToResponse(tenant);
        });
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addTenant(TenantRequest request) {
        runIgnore(() -> {
            // code 唯一性校验
            if (lambdaQuery().eq(SysTenant::getCode, request.getCode()).exists()) {
                throw new BusinessException(ResultCode.DATA_EXISTS.getCode(), "租户标识已存在");
            }
            // 套餐存在性校验
            if (request.getPackageId() != null) {
                SysTenantPackage pkg = sysTenantPackageMapper.selectById(request.getPackageId());
                if (pkg == null) {
                    throw new BusinessException(ResultCode.DATA_NOT_FOUND.getCode(), "套餐不存在");
                }
            }

            SysTenant tenant = new SysTenant();
            BeanUtil.copyProperties(request, tenant);
            if (tenant.getStatus() == null) {
                tenant.setStatus(1);
            }
            save(tenant);
            return null;
        });
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateTenant(TenantRequest request) {
        runIgnore(() -> {
            SysTenant tenant = sysTenantMapper.selectById(request.getId());
            if (tenant == null) {
                throw new BusinessException(ResultCode.TENANT_NOT_EXISTS);
            }
            // 修改 code 时校验唯一性
            if (StrUtil.isNotBlank(request.getCode()) && !request.getCode().equals(tenant.getCode())) {
                if (lambdaQuery().eq(SysTenant::getCode, request.getCode()).exists()) {
                    throw new BusinessException(ResultCode.DATA_EXISTS.getCode(), "租户标识已存在");
                }
            }
            // 套餐存在性校验
            if (request.getPackageId() != null && !request.getPackageId().equals(tenant.getPackageId())) {
                SysTenantPackage pkg = sysTenantPackageMapper.selectById(request.getPackageId());
                if (pkg == null) {
                    throw new BusinessException(ResultCode.DATA_NOT_FOUND.getCode(), "套餐不存在");
                }
            }

            BeanUtil.copyProperties(request, tenant);
            updateById(tenant);
            return null;
        });
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteTenants(List<Long> ids) {
        runIgnore(() -> {
            removeByIds(ids);
            return null;
        });
    }

    @Override
    public void changeStatus(Long id, Integer status) {
        runIgnore(() -> {
            SysTenant tenant = sysTenantMapper.selectById(id);
            if (tenant == null) {
                throw new BusinessException(ResultCode.TENANT_NOT_EXISTS);
            }
            tenant.setStatus(status);
            updateById(tenant);
            return null;
        });
    }

    @Override
    public void validTenant(Long tenantId) {
        if (tenantId == null) {
            throw new BusinessException(ResultCode.TENANT_NOT_EXISTS);
        }
        runIgnore(() -> {
            SysTenant tenant = sysTenantMapper.selectById(tenantId);
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
            return null;
        });
    }

    /**
     * 在 ignore=true 上下文中执行（跨租户共享表）
     */
    private <T> T runIgnore(java.util.function.Supplier<T> supplier) {
        boolean prevIgnore = TenantContextHolder.isIgnore();
        try {
            TenantContextHolder.setIgnore(true);
            return supplier.get();
        } finally {
            TenantContextHolder.setIgnore(prevIgnore);
        }
    }

    private TenantResponse convertToResponse(SysTenant tenant) {
        TenantResponse response = new TenantResponse();
        BeanUtil.copyProperties(tenant, response);
        if (tenant.getPackageId() != null) {
            SysTenantPackage pkg = sysTenantPackageMapper.selectById(tenant.getPackageId());
            if (pkg != null) {
                response.setPackageName(pkg.getName());
            }
        }
        return response;
    }
}
