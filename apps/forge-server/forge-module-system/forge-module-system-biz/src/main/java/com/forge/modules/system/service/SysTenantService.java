package com.forge.modules.system.service;

import com.forge.modules.system.entity.SysTenant;

/**
 * 租户服务接口
 *
 * @author standadmin
 */
public interface SysTenantService {

    /**
     * 根据租户标识查询租户ID
     *
     * @param code 租户标识
     * @return 租户ID，不存在返回 null
     */
    Long getIdByCode(String code);

    /**
     * 获取租户实体
     *
     * @param id 租户ID
     * @return 租户实体，不存在返回 null
     */
    SysTenant getById(Long id);
}
