package com.forge.modules.system.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.forge.common.exception.BusinessException;
import com.forge.common.response.ResultCode;
import com.forge.framework.tenant.core.context.TenantContextHolder;
import com.forge.modules.system.dto.tenant.TenantPackageQueryRequest;
import com.forge.modules.system.dto.tenant.TenantPackageRequest;
import com.forge.modules.system.dto.tenant.TenantPackageResponse;
import com.forge.modules.system.entity.SysTenantPackage;
import com.forge.modules.system.entity.SysTenantPackageMenu;
import com.forge.modules.system.mapper.SysTenantPackageMapper;
import com.forge.modules.system.mapper.SysTenantPackageMenuMapper;
import com.forge.modules.system.service.SysTenantPackageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 租户套餐服务实现
 *
 * @author standadmin
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysTenantPackageServiceImpl extends ServiceImpl<SysTenantPackageMapper, SysTenantPackage>
        implements SysTenantPackageService {

    private final SysTenantPackageMapper sysTenantPackageMapper;
    private final SysTenantPackageMenuMapper sysTenantPackageMenuMapper;

    @Override
    public Page<TenantPackageResponse> pagePackages(TenantPackageQueryRequest request) {
        // 套餐表跨租户共享
        boolean prevIgnore = TenantContextHolder.isIgnore();
        try {
            TenantContextHolder.setIgnore(true);

            Page<SysTenantPackage> page = new Page<>(request.getPageNum(), request.getPageSize());
            LambdaQueryWrapper<SysTenantPackage> wrapper = new LambdaQueryWrapper<>();
            wrapper.like(StrUtil.isNotBlank(request.getName()), SysTenantPackage::getName, request.getName())
                    .like(StrUtil.isNotBlank(request.getCode()), SysTenantPackage::getCode, request.getCode())
                    .eq(request.getStatus() != null, SysTenantPackage::getStatus, request.getStatus())
                    .orderByDesc(SysTenantPackage::getCreateTime);

            Page<SysTenantPackage> pkgPage = sysTenantPackageMapper.selectPage(page, wrapper);

            Page<TenantPackageResponse> responsePage = new Page<>();
            responsePage.setCurrent(pkgPage.getCurrent());
            responsePage.setSize(pkgPage.getSize());
            responsePage.setTotal(pkgPage.getTotal());
            responsePage.setRecords(pkgPage.getRecords().stream()
                    .map(this::convertToResponse)
                    .collect(Collectors.toList()));
            return responsePage;
        } finally {
            TenantContextHolder.setIgnore(prevIgnore);
        }
    }

    @Override
    public TenantPackageResponse getPackageDetail(Long id) {
        boolean prevIgnore = TenantContextHolder.isIgnore();
        try {
            TenantContextHolder.setIgnore(true);
            SysTenantPackage pkg = sysTenantPackageMapper.selectById(id);
            if (pkg == null) {
                throw new BusinessException("套餐不存在");
            }
            return convertToResponse(pkg);
        } finally {
            TenantContextHolder.setIgnore(prevIgnore);
        }
    }

    @Override
    public List<TenantPackageResponse> getAllEnabledPackages() {
        boolean prevIgnore = TenantContextHolder.isIgnore();
        try {
            TenantContextHolder.setIgnore(true);
            return lambdaQuery().eq(SysTenantPackage::getStatus, 1)
                    .orderByDesc(SysTenantPackage::getCreateTime)
                    .list()
                    .stream()
                    .map(this::convertToResponse)
                    .collect(Collectors.toList());
        } finally {
            TenantContextHolder.setIgnore(prevIgnore);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addPackage(TenantPackageRequest request) {
        boolean prevIgnore = TenantContextHolder.isIgnore();
        try {
            TenantContextHolder.setIgnore(true);

            // 套餐编码唯一性校验
            if (lambdaQuery().eq(SysTenantPackage::getCode, request.getCode()).exists()) {
                throw new BusinessException(ResultCode.DATA_EXISTS.getCode(), "套餐编码已存在");
            }

            SysTenantPackage pkg = new SysTenantPackage();
            BeanUtil.copyProperties(request, pkg);
            if (pkg.getStatus() == null) {
                pkg.setStatus(1);
            }
            save(pkg);

            // 保存套餐菜单关联
            savePackageMenus(pkg.getId(), request.getMenuIds());
        } finally {
            TenantContextHolder.setIgnore(prevIgnore);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updatePackage(TenantPackageRequest request) {
        boolean prevIgnore = TenantContextHolder.isIgnore();
        try {
            TenantContextHolder.setIgnore(true);

            SysTenantPackage pkg = sysTenantPackageMapper.selectById(request.getId());
            if (pkg == null) {
                throw new BusinessException("套餐不存在");
            }

            // 套餐编码修改时校验唯一性
            if (StrUtil.isNotBlank(request.getCode()) && !request.getCode().equals(pkg.getCode())) {
                if (lambdaQuery().eq(SysTenantPackage::getCode, request.getCode()).exists()) {
                    throw new BusinessException(ResultCode.DATA_EXISTS.getCode(), "套餐编码已存在");
                }
            }

            BeanUtil.copyProperties(request, pkg);
            updateById(pkg);

            // 重建菜单关联
            sysTenantPackageMenuMapper.deleteByPackageId(pkg.getId());
            savePackageMenus(pkg.getId(), request.getMenuIds());
        } finally {
            TenantContextHolder.setIgnore(prevIgnore);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deletePackages(List<Long> ids) {
        boolean prevIgnore = TenantContextHolder.isIgnore();
        try {
            TenantContextHolder.setIgnore(true);
            removeByIds(ids);
            ids.forEach(sysTenantPackageMenuMapper::deleteByPackageId);
        } finally {
            TenantContextHolder.setIgnore(prevIgnore);
        }
    }

    @Override
    public void updateStatus(Long id, Integer status) {
        boolean prevIgnore = TenantContextHolder.isIgnore();
        try {
            TenantContextHolder.setIgnore(true);
            SysTenantPackage pkg = sysTenantPackageMapper.selectById(id);
            if (pkg == null) {
                throw new BusinessException("套餐不存在");
            }
            pkg.setStatus(status);
            updateById(pkg);
        } finally {
            TenantContextHolder.setIgnore(prevIgnore);
        }
    }

    @Override
    public List<Long> getMenuIdsByPackageId(Long packageId) {
        if (packageId == null) {
            return List.of();
        }
        boolean prevIgnore = TenantContextHolder.isIgnore();
        try {
            TenantContextHolder.setIgnore(true);
            return sysTenantPackageMenuMapper.selectMenuIdsByPackageId(packageId);
        } finally {
            TenantContextHolder.setIgnore(prevIgnore);
        }
    }

    private void savePackageMenus(Long packageId, List<Long> menuIds) {
        if (menuIds == null || menuIds.isEmpty()) {
            return;
        }
        List<SysTenantPackageMenu> list = menuIds.stream().map(menuId -> {
            SysTenantPackageMenu rel = new SysTenantPackageMenu();
            rel.setTenantPackageId(packageId);
            rel.setMenuId(menuId);
            return rel;
        }).collect(Collectors.toList());
        sysTenantPackageMenuMapper.batchInsert(list);
    }

    private TenantPackageResponse convertToResponse(SysTenantPackage pkg) {
        TenantPackageResponse response = new TenantPackageResponse();
        BeanUtil.copyProperties(pkg, response);
        response.setMenuIds(sysTenantPackageMenuMapper.selectMenuIdsByPackageId(pkg.getId()));
        return response;
    }
}
