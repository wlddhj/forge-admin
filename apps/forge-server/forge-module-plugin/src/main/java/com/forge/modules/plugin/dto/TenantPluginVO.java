package com.forge.modules.plugin.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * 租户插件状态视图（授权抽屉行数据）。
 *
 * @author forge-admin
 */
@Data
@Builder
public class TenantPluginVO {

    private Long tenantId;

    private String tenantName;

    /** 0停用 1启用 */
    private Integer enabled;

    /** 停用原因（手动/license_expired/license_revoked） */
    private String disabledReason;

    private LocalDateTime enabledTime;

    private LocalDateTime disabledTime;

    /** 当前有效 License 编号（null=无有效授权） */
    private String licenseKey;

    private String licensee;

    private LocalDateTime notAfter;

    /** 即将到期预警（7 天内） */
    private Boolean expiringSoon;

    /** 有效 scope 全集（声明默认 ∪ 勾选 ∪ License 内置） */
    private Set<String> effectiveScopes;
}
