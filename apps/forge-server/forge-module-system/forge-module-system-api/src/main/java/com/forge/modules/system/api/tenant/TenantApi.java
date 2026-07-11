package com.forge.modules.system.api.tenant;

import java.util.List;

/**
 * 租户 RPC 接口（system-biz 实现，供其他模块调用）
 */
public interface TenantApi {

    /**
     * 校验租户是否合法
     *
     * @param tenantId 租户ID
     * @throws com.forge.common.exception.BusinessException 不合法时抛出
     */
    void validTenant(Long tenantId);

    /**
     * 获取租户名称
     */
    String getTenantName(Long tenantId);

    /**
     * 获取租户套餐的菜单ID列表
     */
    List<Long> getTenantPackageMenuIds(Long tenantId);
}