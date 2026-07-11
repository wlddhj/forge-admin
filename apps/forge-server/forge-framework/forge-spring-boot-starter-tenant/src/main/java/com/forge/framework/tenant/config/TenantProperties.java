package com.forge.framework.tenant.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Collections;
import java.util.Set;

@Data
@ConfigurationProperties(prefix = "forge.tenant")
public class TenantProperties {

    /**
     * 是否开启多租户（默认 true）
     */
    private Boolean enable = true;

    /**
     * 请求头名称（默认 X-Tenant-Id）
     */
    private String header = "X-Tenant-Id";

    /**
     * 忽略租户校验的 URL（白名单）
     */
    private Set<String> ignoreUrls = Collections.emptySet();

    /**
     * 跨租户共享的表（不注入 tenant_id）
     */
    private Set<String> ignoreTables = Collections.emptySet();

    /**
     * 跨租户共享的缓存（key 不加 tenantId 前缀）
     */
    private Set<String> ignoreCaches = Collections.emptySet();
}