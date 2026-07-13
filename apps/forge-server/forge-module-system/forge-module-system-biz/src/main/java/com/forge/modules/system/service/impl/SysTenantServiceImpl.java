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
import com.forge.modules.system.entity.SysUser;
import com.forge.modules.system.entity.SysUserRole;
import com.forge.modules.system.mapper.SysRoleMapper;
import com.forge.modules.system.mapper.SysTenantMapper;
import com.forge.modules.system.mapper.SysTenantPackageMapper;
import com.forge.modules.system.mapper.SysUserMapper;
import com.forge.modules.system.mapper.SysUserRoleMapper;
import com.forge.modules.system.service.SysTenantService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
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

    /** 租户管理员角色 code */
    private static final String TENANT_ADMIN_ROLE_CODE = "TENANT_ADMIN";

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String PASSWORD_CHARS =
            "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789!@#$%^&*";

    private final SysTenantMapper sysTenantMapper;
    private final SysTenantPackageMapper sysTenantPackageMapper;
    private final SysUserMapper sysUserMapper;
    private final SysUserRoleMapper sysUserRoleMapper;
    private final SysRoleMapper sysRoleMapper;
    private final PasswordEncoder passwordEncoder;

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
    public TenantResponse addTenant(TenantRequest request) {
        // 1. 校验：code 唯一性 + 套餐存在性
        if (lambdaQuery().eq(SysTenant::getCode, request.getCode()).exists()) {
            throw new BusinessException(ResultCode.DATA_EXISTS.getCode(), "租户标识已存在");
        }
        if (request.getPackageId() != null) {
            SysTenantPackage pkg = sysTenantPackageMapper.selectById(request.getPackageId());
            if (pkg == null) {
                throw new BusinessException(ResultCode.DATA_NOT_FOUND.getCode(), "套餐不存在");
            }
        }

        // 2. 计算管理员用户名与密码
        String adminUsername = StrUtil.blankToDefault(request.getAdminUsername(), "admin");
        String rawPassword = StrUtil.isNotBlank(request.getAdminPassword())
                ? request.getAdminPassword()
                : generateRandomPassword(16);

        // 3. 创建租户（sys_tenant 在 ignore-tables 中，不会触发 TenantLineInnerInterceptor）
        //    即使 setIgnore 状态被重置，sys_tenant 自身也不需要 tenant_id
        Long tenantId;
        SysTenant tenant = new SysTenant();
        BeanUtil.copyProperties(request, tenant);
        if (tenant.getStatus() == null) {
            tenant.setStatus(1);
        }
        save(tenant);
        tenantId = tenant.getId();

        // 4. 创建租户管理员用户（account_type=0，区别于平台超管 account_type=2）
        SysUser admin = new SysUser();
        admin.setTenantId(tenantId);
        admin.setUsername(adminUsername);
        admin.setNickname("租户管理员");
        admin.setPassword(passwordEncoder.encode(rawPassword));
        admin.setStatus(1);
        admin.setAccountType(0);
        admin.setFirstLogin(1);  // 首次登录强制改密
        admin.setDeleted(0);
        sysUserMapper.insert(admin);
        log.info("[租户创建] 自动生成管理员账号: tenantId={}, username={}", tenantId, adminUsername);

        // 5. 关联 TENANT_ADMIN 角色
        Long roleId = sysRoleMapper.selectIdByCode(TENANT_ADMIN_ROLE_CODE);
        if (roleId == null) {
            log.warn("[租户创建] TENANT_ADMIN 角色不存在（id=3），管理员账号已建但无角色关联。请手动执行 V2026071301 脚本");
        } else {
            SysUserRole userRole = new SysUserRole();
            userRole.setTenantId(tenantId);
            userRole.setUserId(admin.getId());
            userRole.setRoleId(roleId);
            sysUserRoleMapper.insert(userRole);
        }

        // 6. 返回响应（含明文密码，仅创建时返回一次）
        TenantResponse response = new TenantResponse();
        BeanUtil.copyProperties(request, response);
        response.setId(tenantId);
        response.setInitialAdminPassword(rawPassword);
        if (request.getPackageId() != null) {
            SysTenantPackage pkg = sysTenantPackageMapper.selectById(request.getPackageId());
            if (pkg != null) {
                response.setPackageName(pkg.getName());
            }
        }
        return response;
    }

    /**
     * 生成指定长度的强密码（大小写字母+数字+特殊符号，至少各 1 个）
     */
    private String generateRandomPassword(int length) {
        StringBuilder sb = new StringBuilder(length);
        sb.append("ABCDEFGHJKLMNPQRSTUVWXYZ".charAt(RANDOM.nextInt(23)));
        sb.append("abcdefghjkmnpqrstuvwxyz".charAt(RANDOM.nextInt(23)));
        sb.append("23456789".charAt(RANDOM.nextInt(8)));
        sb.append("!@#$%^&*".charAt(RANDOM.nextInt(8)));
        for (int i = 4; i < length; i++) {
            sb.append(PASSWORD_CHARS.charAt(RANDOM.nextInt(PASSWORD_CHARS.length())));
        }
        // Fisher-Yates 打乱顺序，避免前 4 位固定
        char[] arr = sb.toString().toCharArray();
        for (int i = arr.length - 1; i > 0; i--) {
            int j = RANDOM.nextInt(i + 1);
            char tmp = arr[i];
            arr[i] = arr[j];
            arr[j] = tmp;
        }
        return new String(arr);
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
