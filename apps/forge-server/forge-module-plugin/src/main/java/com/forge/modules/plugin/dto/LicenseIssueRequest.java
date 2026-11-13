package com.forge.modules.plugin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * License 在线签发请求。
 *
 * @author forge-admin
 */
@Data
public class LicenseIssueRequest {

    /** 目标租户 */
    @NotNull
    private Long tenantId;

    /** 授权插件编码（一码多插件） */
    @NotEmpty
    private List<String> pluginCodes;

    /** 被授权方名称（公司主体，展示） */
    @NotBlank
    private String licensee;

    /** 生效起（ISO-8601 含时区，如 2026-11-13T00:00:00+08:00；空=立即生效） */
    private String notBefore;

    /** 生效止（必填，ISO-8601 含时区） */
    @NotBlank
    private String notAfter;

    /** 内置数据 scope（须 ⊆ 每个所选插件声明集，可空） */
    private List<String> scopes;

    /** License 编号（空则自动生成 LIC-yyyyMMdd-XXXX） */
    private String licenseKey;

    /** true=签发并落库生效；false=仅生成内容供下载 */
    private boolean activate;
}
