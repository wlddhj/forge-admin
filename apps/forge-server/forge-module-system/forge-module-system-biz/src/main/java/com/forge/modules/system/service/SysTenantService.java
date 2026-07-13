package com.forge.modules.system.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.IService;
import com.forge.modules.system.dto.tenant.TenantQueryRequest;
import com.forge.modules.system.dto.tenant.TenantRequest;
import com.forge.modules.system.dto.tenant.TenantResponse;
import com.forge.modules.system.entity.SysTenant;

import java.util.List;

/**
 * 租户服务接口
 *
 * @author standadmin
 */
public interface SysTenantService extends IService<SysTenant> {

    /**
     * 根据租户标识查询租户ID
     *
     * @param code 租户标识
     * @return 租户ID，不存在返回 null
     */
    Long getIdByCode(String code);

    /**
     * 根据租户标识查询租户实体
     *
     * @param code 租户标识
     * @return 租户实体，不存在返回 null
     */
    SysTenant getByCode(String code);

    /**
     * 获取租户实体
     *
     * @param id 租户ID
     * @return 租户实体，不存在返回 null
     */
    SysTenant getById(Long id);

    /**
     * 分页查询租户
     */
    Page<TenantResponse> pageTenants(TenantQueryRequest request);

    /**
     * 获取租户详情
     */
    TenantResponse getTenantDetail(Long id);

    /**
     * 新增租户（自动生成租户管理员账号并关联 TENANT_ADMIN 角色）
     *
     * @return 包含初始管理员明文密码（仅本次返回）
     */
    TenantResponse addTenant(TenantRequest request);

    /**
     * 更新租户
     */
    void updateTenant(TenantRequest request);

    /**
     * 删除租户
     */
    void deleteTenants(List<Long> ids);

    /**
     * 更新租户状态
     */
    void changeStatus(Long id, Integer status);

    /**
     * 校验租户是否合法（状态启用且未过期）
     *
     * @param tenantId 租户ID
     * @throws com.forge.common.exception.BusinessException 不合法时抛出
     */
    void validTenant(Long tenantId);
}
