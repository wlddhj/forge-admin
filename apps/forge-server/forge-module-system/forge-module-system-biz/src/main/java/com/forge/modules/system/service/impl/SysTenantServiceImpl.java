package com.forge.modules.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.forge.framework.tenant.core.context.TenantContextHolder;
import com.forge.modules.system.entity.SysTenant;
import com.forge.modules.system.mapper.SysTenantMapper;
import com.forge.modules.system.service.SysTenantService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

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

    @Override
    public Long getIdByCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        // 登录阶段 TenantContextHolder 尚未设置，使用 ignore 跳过租户拦截器
        boolean prevIgnore = TenantContextHolder.isIgnore();
        try {
            TenantContextHolder.setIgnore(true);
            return sysTenantMapper.selectIdByCode(code);
        } finally {
            TenantContextHolder.setIgnore(prevIgnore);
        }
    }

    @Override
    public SysTenant getById(Long id) {
        if (id == null) {
            return null;
        }
        boolean prevIgnore = TenantContextHolder.isIgnore();
        try {
            TenantContextHolder.setIgnore(true);
            return sysTenantMapper.selectById(id);
        } finally {
            TenantContextHolder.setIgnore(prevIgnore);
        }
    }
}
