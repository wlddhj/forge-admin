package com.forge.modules.system.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.forge.modules.system.dto.tenant.TenantPackageQueryRequest;
import com.forge.modules.system.dto.tenant.TenantPackageRequest;
import com.forge.modules.system.dto.tenant.TenantPackageResponse;
import com.forge.modules.system.entity.SysTenantPackage;

import java.util.List;

/**
 * 租户套餐服务接口
 *
 * @author standadmin
 */
public interface SysTenantPackageService extends IService<SysTenantPackage> {

    /**
     * 分页查询租户套餐
     */
    Page<TenantPackageResponse> pagePackages(TenantPackageQueryRequest request);

    /**
     * 获取套餐详情
     */
    TenantPackageResponse getPackageDetail(Long id);

    /**
     * 获取所有启用的套餐
     */
    List<TenantPackageResponse> getAllEnabledPackages();

    /**
     * 新增套餐
     */
    void addPackage(TenantPackageRequest request);

    /**
     * 更新套餐
     */
    void updatePackage(TenantPackageRequest request);

    /**
     * 删除套餐
     */
    void deletePackages(List<Long> ids);

    /**
     * 更新套餐状态
     */
    void updateStatus(Long id, Integer status);

    /**
     * 获取套餐的菜单ID列表
     */
    List<Long> getMenuIdsByPackageId(Long packageId);
}
