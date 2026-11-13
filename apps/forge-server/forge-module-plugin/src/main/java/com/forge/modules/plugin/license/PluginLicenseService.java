package com.forge.modules.plugin.license;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.forge.common.exception.BusinessException;
import com.forge.common.response.ResultCode;
import com.forge.framework.plugin.descriptor.PluginDescriptor;
import com.forge.framework.plugin.license.LicenseIssuer;
import com.forge.framework.plugin.license.LicensePayload;
import com.forge.framework.plugin.license.LicenseVerifier;
import com.forge.framework.plugin.descriptor.ScopeDefinition;
import com.forge.modules.plugin.dto.LicenseIssueRequest;
import com.forge.modules.plugin.entity.SysPlugin;
import com.forge.modules.plugin.entity.SysPluginLicense;
import com.forge.modules.plugin.entity.SysPluginTenant;
import com.forge.modules.plugin.mapper.SysPluginLicenseMapper;
import com.forge.modules.plugin.mapper.SysPluginMapper;
import com.forge.modules.plugin.mapper.SysPluginTenantMapper;
import com.forge.modules.plugin.state.PluginCacheService;
import com.forge.modules.system.entity.SysTenant;
import com.forge.modules.system.service.SysTenantService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 插件 License 服务：导入验签落库 / 撤销联动停用 / 有效授权判断。
 *
 * <p>校验三要素（02 设计 Q5：签名 + 租户 + 时间窗）外加结构校验
 * （scope ⊆ 插件声明集）。一码多插件原子导入：任一插件未注册则整文件拒绝。
 *
 * @author forge-admin
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PluginLicenseService {

    private final LicenseVerifier licenseVerifier;

    /** 在线签发器（未配置私钥时为空 Provider，签发能力不暴露） */
    private final ObjectProvider<LicenseIssuer> licenseIssuerProvider;

    private final SysPluginLicenseMapper licenseMapper;

    private final SysPluginMapper pluginMapper;

    private final SysPluginTenantMapper tenantPluginMapper;

    private final SysTenantService sysTenantService;

    private final PluginCacheService pluginCacheService;

    private final List<PluginDescriptor> descriptors;

    /**
     * 导入 License 文件：验签 → 全字段校验 → 按插件拆行落库 → 顶替旧授权。
     *
     * @return 导入结果（成功插件数，供前端展示）
     */
    @Transactional(rollbackFor = Exception.class)
    public int importLicense(String licenseFileContent) {
        LicensePayload payload = licenseVerifier.verify(licenseFileContent);
        validatePayload(payload);

        Long tenantId = resolveTenantId(payload.tenantCode());
        LocalDateTime now = LocalDateTime.now();
        // 去重防 uk_license 冲突（同 key 同 code 拆两行会撞唯一索引报 DB 错而非业务错）
        List<String> pluginCodes = payload.pluginCodes().stream().distinct().toList();

        for (String pluginCode : pluginCodes) {
            // 重复导入检查（license_key + plugin_code）
            Long duplicated = licenseMapper.selectCount(Wrappers.<SysPluginLicense>lambdaQuery()
                    .eq(SysPluginLicense::getLicenseKey, payload.licenseKey())
                    .eq(SysPluginLicense::getPluginCode, pluginCode));
            if (duplicated > 0) {
                throw new BusinessException(ResultCode.LICENSE_DUPLICATED,
                        "License " + payload.licenseKey() + " 已导入过（插件 " + pluginCode + "）");
            }
        }

        // 旧有效授权顶替 + 新记录落库
        for (String pluginCode : pluginCodes) {
            SysPluginLicense replaced = licenseMapper.selectOne(Wrappers.<SysPluginLicense>lambdaQuery()
                    .eq(SysPluginLicense::getTenantId, tenantId)
                    .eq(SysPluginLicense::getPluginCode, pluginCode)
                    .eq(SysPluginLicense::getStatus, SysPluginLicense.STATUS_ACTIVE)
                    .last("LIMIT 1"));
            if (replaced != null) {
                SysPluginLicense patch = new SysPluginLicense();
                patch.setId(replaced.getId());
                patch.setStatus(SysPluginLicense.STATUS_REPLACED);
                patch.setUpdateTime(now);
                licenseMapper.updateById(patch);
            }

            SysPluginLicense record = new SysPluginLicense();
            record.setLicenseKey(payload.licenseKey());
            record.setTenantId(tenantId);
            record.setTenantCode(payload.tenantCode());
            record.setLicenseeName(payload.licensee());
            record.setPluginCode(pluginCode);
            record.setScopes(payload.scopes() == null ? null : String.join(",", payload.scopes()));
            record.setNotBefore(toLocalDateTime(payload.notBefore(), "notBefore"));
            record.setNotAfter(toLocalDateTime(payload.notAfter(), "notAfter"));
            record.setIssuedAt(toLocalDateTime(payload.issuedAt(), "issuedAt"));
            record.setStatus(SysPluginLicense.STATUS_ACTIVE);
            record.setVerifyState(1);
            record.setVerifyTime(now);
            record.setPayloadText(licenseFileContent);
            record.setSignature(extractSignature(licenseFileContent));
            record.setCreateTime(now);
            record.setUpdateTime(now);
            licenseMapper.insert(record);

            pluginCacheService.evictScope(tenantId, pluginCode);
        }
        log.info("License {} 导入成功: 租户 {}，插件 {}，有效期 {} ~ {}",
                payload.licenseKey(), payload.tenantCode(), pluginCodes, payload.notBefore(), payload.notAfter());
        return pluginCodes.size();
    }

    /** 撤销 License：记录失效 + 联动停用对应租户插件（若启用中） */
    @Transactional(rollbackFor = Exception.class)
    public void revoke(Long licenseId) {
        SysPluginLicense license = licenseMapper.selectById(licenseId);
        if (license == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "License 记录不存在");
        }
        LocalDateTime now = LocalDateTime.now();
        SysPluginLicense patch = new SysPluginLicense();
        patch.setId(license.getId());
        patch.setStatus(SysPluginLicense.STATUS_INVALID);
        patch.setVerifyState(0);
        patch.setFailReason("手动撤销");
        patch.setVerifyTime(now);
        patch.setUpdateTime(now);
        licenseMapper.updateById(patch);

        // 联动停用（fail-closed：撤销即停）
        SysPluginTenant tenantPlugin = tenantPluginMapper.selectOne(Wrappers.<SysPluginTenant>lambdaQuery()
                .eq(SysPluginTenant::getTenantId, license.getTenantId())
                .eq(SysPluginTenant::getPluginCode, license.getPluginCode())
                .eq(SysPluginTenant::getEnabled, 1)
                .last("LIMIT 1"));
        if (tenantPlugin != null) {
            SysPluginTenant disable = new SysPluginTenant();
            disable.setId(tenantPlugin.getId());
            disable.setEnabled(0);
            disable.setDisabledReason("license_revoked");
            disable.setDisabledTime(now);
            disable.setUpdateTime(now);
            tenantPluginMapper.updateById(disable);
            pluginCacheService.evictState(license.getTenantId(), license.getPluginCode());
            pluginCacheService.evictMenuCache();
        }
        pluginCacheService.evictScope(license.getTenantId(), license.getPluginCode());
    }

    /** 当前时刻是否存在有效授权（启用动作与 scope 合并的判定基础） */
    public boolean hasActiveLicense(Long tenantId, String pluginCode, LocalDateTime at) {
        SysPluginLicense active = licenseMapper.selectOne(Wrappers.<SysPluginLicense>lambdaQuery()
                .eq(SysPluginLicense::getTenantId, tenantId)
                .eq(SysPluginLicense::getPluginCode, pluginCode)
                .eq(SysPluginLicense::getStatus, SysPluginLicense.STATUS_ACTIVE)
                .le(SysPluginLicense::getNotBefore, at)
                .gt(SysPluginLicense::getNotAfter, at)
                .last("LIMIT 1"));
        return active != null;
    }

    /** 有效授权的内置 scope 集（scope 合并口径之一） */
    public Set<String> getActiveLicenseScopes(Long tenantId, String pluginCode, LocalDateTime at) {
        List<SysPluginLicense> actives = licenseMapper.selectList(Wrappers.<SysPluginLicense>lambdaQuery()
                .eq(SysPluginLicense::getTenantId, tenantId)
                .eq(SysPluginLicense::getPluginCode, pluginCode)
                .eq(SysPluginLicense::getStatus, SysPluginLicense.STATUS_ACTIVE)
                .le(SysPluginLicense::getNotBefore, at)
                .gt(SysPluginLicense::getNotAfter, at));
        return actives.stream()
                .filter(l -> l.getScopes() != null && !l.getScopes().isBlank())
                .flatMap(l -> Arrays.stream(l.getScopes().split(",")))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
    }

    /** 到期巡检：过期授权置失效并自动停用对应租户插件（Quartz 每日调用） */
    @Transactional(rollbackFor = Exception.class)
    public int expireOverdueLicenses() {
        LocalDateTime now = LocalDateTime.now();
        List<SysPluginLicense> overdue = licenseMapper.selectList(Wrappers.<SysPluginLicense>lambdaQuery()
                .eq(SysPluginLicense::getStatus, SysPluginLicense.STATUS_ACTIVE)
                .le(SysPluginLicense::getNotAfter, now));
        int disabled = 0;
        for (SysPluginLicense license : overdue) {
            SysPluginLicense patch = new SysPluginLicense();
            patch.setId(license.getId());
            patch.setStatus(SysPluginLicense.STATUS_INVALID);
            patch.setVerifyState(0);
            patch.setFailReason("已到期");
            patch.setVerifyTime(now);
            patch.setUpdateTime(now);
            licenseMapper.updateById(patch);

            SysPluginTenant tenantPlugin = tenantPluginMapper.selectOne(Wrappers.<SysPluginTenant>lambdaQuery()
                    .eq(SysPluginTenant::getTenantId, license.getTenantId())
                    .eq(SysPluginTenant::getPluginCode, license.getPluginCode())
                    .eq(SysPluginTenant::getEnabled, 1)
                    .last("LIMIT 1"));
            if (tenantPlugin != null) {
                SysPluginTenant disable = new SysPluginTenant();
                disable.setId(tenantPlugin.getId());
                disable.setEnabled(0);
                disable.setDisabledReason(SysPluginTenant.REASON_LICENSE_EXPIRED);
                disable.setDisabledTime(now);
                disable.setUpdateTime(now);
                tenantPluginMapper.updateById(disable);
                pluginCacheService.evictState(license.getTenantId(), license.getPluginCode());
                pluginCacheService.evictScope(license.getTenantId(), license.getPluginCode());
                disabled++;
            }
            log.info("License {} 已到期自动失效: 租户 {} 插件 {}", license.getLicenseKey(), license.getTenantId(), license.getPluginCode());
        }
        if (disabled > 0) {
            pluginCacheService.evictMenuCache();
        }
        return disabled;
    }

    /**
     * 在线签发（双模式：未配置私钥即未启用）。
     *
     * @param request 签发表单
     * @return licenseKey / 是否已落库生效 / 文件内容（activate=false 时前端下载）
     */
    public Map<String, Object> issue(LicenseIssueRequest request) {
        LicenseIssuer issuer = licenseIssuerProvider.getIfAvailable();
        if (issuer == null) {
            throw new BusinessException(ResultCode.LICENSE_INVALID, "在线签发未启用（未配置 forge.plugin.license.private-key）");
        }
        // 租户与时间窗
        SysTenant tenant = sysTenantService.getById(request.getTenantId());
        if (tenant == null || !Integer.valueOf(1).equals(tenant.getStatus())) {
            throw new BusinessException(ResultCode.LICENSE_TENANT_MISMATCH, "租户不存在或已被禁用");
        }
        LocalDateTime now = LocalDateTime.now();
        String notBeforeIso = (request.getNotBefore() == null || request.getNotBefore().isBlank())
                ? toIso(now) : request.getNotBefore();
        String licenseKey = (request.getLicenseKey() == null || request.getLicenseKey().isBlank())
                ? generateLicenseKey(now) : request.getLicenseKey().trim();

        LicensePayload payload = new LicensePayload(
                licenseKey,
                request.getPluginCodes().stream().distinct().toList(),
                tenant.getCode(),
                request.getLicensee(),
                toIso(now),
                notBeforeIso,
                request.getNotAfter(),
                request.getScopes(),
                null);
        validatePayload(payload);
        // 唯一性前置（activate=false 的下载版也须可导入，避免签出即撞号）
        for (String pluginCode : payload.pluginCodes()) {
            Long duplicated = licenseMapper.selectCount(Wrappers.<SysPluginLicense>lambdaQuery()
                    .eq(SysPluginLicense::getLicenseKey, licenseKey)
                    .eq(SysPluginLicense::getPluginCode, pluginCode));
            if (duplicated > 0) {
                throw new BusinessException(ResultCode.LICENSE_DUPLICATED,
                        "License " + licenseKey + " 已存在（插件 " + pluginCode + "）");
            }
        }

        String content = issuer.issue(payload);
        boolean activated = false;
        if (request.isActivate()) {
            importLicense(content);
            activated = true;
        }
        log.info("License {} 在线签发: 租户 {} 插件 {} activate={}",
                licenseKey, tenant.getCode(), payload.pluginCodes(), activated);
        Map<String, Object> result = new HashMap<>();
        result.put("licenseKey", licenseKey);
        result.put("activated", activated);
        result.put("licenseContent", content);
        return result;
    }

    private String generateLicenseKey(LocalDateTime now) {
        return "LIC-" + now.format(DateTimeFormatter.ofPattern("yyyyMMdd"))
                + "-" + Integer.toHexString((int) (System.nanoTime() & 0xFFFF)).toUpperCase();
    }

    private String toIso(LocalDateTime localDateTime) {
        return localDateTime.atZone(ZoneId.systemDefault()).toOffsetDateTime().toString();
    }

    /** 签发表单元数据：能力开关 + 启用租户 + 注册插件（含声明 scope 集） */
    public Map<String, Object> buildIssueFormData() {
        Map<String, Object> data = new HashMap<>();
        data.put("enabled", licenseIssuerProvider.getIfAvailable() != null);
        data.put("tenants", sysTenantService.list().stream()
                .filter(t -> Integer.valueOf(1).equals(t.getStatus()))
                .map(t -> Map.of("id", t.getId(), "name", t.getName(), "code", t.getCode()))
                .toList());
        List<Map<String, Object>> plugins = new ArrayList<>();
        for (PluginDescriptor descriptor : descriptors) {
            Map<String, Object> plugin = new HashMap<>();
            plugin.put("pluginCode", descriptor.pluginCode());
            plugin.put("name", descriptor.name());
            plugin.put("declaredScopes", descriptor.declaredScopes());
            plugins.add(plugin);
        }
        data.put("plugins", plugins);
        return data;
    }

    /** License 记录分页（管理端列表） */
    public Page<SysPluginLicense> pageLicenses(int pageNum, int pageSize, Long tenantId, String pluginCode, Integer status) {
        return licenseMapper.selectPage(
                new Page<>(pageNum, pageSize),
                Wrappers.<SysPluginLicense>lambdaQuery()
                        .eq(tenantId != null, SysPluginLicense::getTenantId, tenantId)
                        .eq(pluginCode != null && !pluginCode.isBlank(), SysPluginLicense::getPluginCode, pluginCode)
                        .eq(status != null, SysPluginLicense::getStatus, status)
                        .orderByDesc(SysPluginLicense::getId));
    }

    // ===== 内部 =====

    private void validatePayload(LicensePayload payload) {
        if (payload.licenseKey() == null || payload.licenseKey().isBlank()) {
            throw new BusinessException(ResultCode.LICENSE_INVALID, "License 缺少 licenseKey");
        }
        if (payload.pluginCodes() == null || payload.pluginCodes().isEmpty()) {
            throw new BusinessException(ResultCode.LICENSE_INVALID, "License 未授权任何插件（pluginCodes 为空）");
        }
        if (payload.tenantCode() == null || payload.tenantCode().isBlank()) {
            throw new BusinessException(ResultCode.LICENSE_INVALID, "License 缺少租户编码 tenantCode");
        }
        LocalDateTime notBefore = toLocalDateTime(payload.notBefore(), "notBefore");
        LocalDateTime notAfter = toLocalDateTime(payload.notAfter(), "notAfter");
        LocalDateTime now = LocalDateTime.now();
        if (notBefore.isAfter(now)) {
            throw new BusinessException(ResultCode.LICENSE_NOT_YET_VALID,
                    "License 未到生效期（" + payload.notBefore() + "）");
        }
        if (!notAfter.isAfter(now)) {
            throw new BusinessException(ResultCode.LICENSE_EXPIRED,
                    "License 已过期（" + payload.notAfter() + "）");
        }
        // 插件注册性
        for (String pluginCode : payload.pluginCodes()) {
            Long registered = pluginMapper.selectCount(Wrappers.<SysPlugin>lambdaQuery()
                    .eq(SysPlugin::getPluginCode, pluginCode));
            if (registered == 0) {
                throw new BusinessException(ResultCode.LICENSE_PLUGIN_UNKNOWN,
                        "License 包含未注册的插件: " + pluginCode);
            }
        }
        // scope ⊆ 各插件声明集
        if (payload.scopes() != null && !payload.scopes().isEmpty()) {
            for (String pluginCode : payload.pluginCodes()) {
                Set<String> declared = declaredScopesOf(pluginCode);
                List<String> exceeded = payload.scopes().stream()
                        .filter(s -> !declared.contains(s))
                        .toList();
                if (!exceeded.isEmpty()) {
                    throw new BusinessException(ResultCode.LICENSE_SCOPE_NOT_DECLARED,
                            "License 内置 scope " + exceeded + " 超出插件[" + pluginCode + "]声明集 " + declared);
                }
            }
        }
    }

    private Long resolveTenantId(String tenantCode) {
        SysTenant tenant = sysTenantService.getByCode(tenantCode);
        if (tenant == null || !Integer.valueOf(1).equals(tenant.getStatus())) {
            throw new BusinessException(ResultCode.LICENSE_TENANT_MISMATCH,
                    "License 绑定的租户不存在或已被禁用: " + tenantCode);
        }
        return tenant.getId();
    }

    private Set<String> declaredScopesOf(String pluginCode) {
        return descriptors.stream()
                .filter(d -> d.pluginCode().equals(pluginCode))
                .findFirst()
                .map(d -> d.declaredScopes().stream().map(ScopeDefinition::scope).collect(Collectors.toSet()))
                .orElseGet(HashSet::new);
    }

    private LocalDateTime toLocalDateTime(String iso, String field) {
        if (iso == null || iso.isBlank()) {
            throw new BusinessException(ResultCode.LICENSE_INVALID, "License 缺少时间字段 " + field);
        }
        try {
            OffsetDateTime offsetDateTime = OffsetDateTime.parse(iso);
            Instant instant = offsetDateTime.toInstant();
            return LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
        } catch (DateTimeParseException e) {
            throw new BusinessException(ResultCode.LICENSE_INVALID,
                    "License 时间字段 " + field + " 非法（须 ISO-8601 含时区偏移）: " + iso);
        }
    }

    private String extractSignature(String licenseFileContent) {
        int idx = licenseFileContent.indexOf("\"signature\"");
        if (idx < 0) {
            return null;
        }
        int colon = licenseFileContent.indexOf(':', idx);
        int startQuote = licenseFileContent.indexOf('"', colon + 1);
        int endQuote = licenseFileContent.indexOf('"', startQuote + 1);
        if (startQuote < 0 || endQuote < 0) {
            return null;
        }
        return licenseFileContent.substring(startQuote + 1, endQuote);
    }
}
