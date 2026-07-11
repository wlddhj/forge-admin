package com.forge.framework.tenant.core.db;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 多租户业务实体基类
 *
 * 业务实体继承此类即可获得 tenantId 字段。
 * 注意：不要让 BaseDO 继承此类——因为 BaseDO 用于跨租户共享表（如菜单、字典）。
 * 业务实体应直接继承 TenantBaseDO。
 */
@Data
public abstract class TenantBaseDO {

    /**
     * 多租户编号
     */
    private Long tenantId;

    /**
     * 创建时间（与 BaseDO 对齐）
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

    /**
     * 创建人
     */
    private Long createBy;

    /**
     * 更新人
     */
    private Long updateBy;

    /**
     * 逻辑删除标记
     */
    private Integer deleted;
}
