package com.forge.modules.plugin.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.forge.common.exception.BusinessException;
import com.forge.common.response.PageResult;
import com.forge.common.response.ResultCode;
import com.forge.modules.plugin.entity.SysPlugin;
import com.forge.modules.plugin.entity.SysPluginLicense;
import com.forge.modules.plugin.entity.SysPluginTenant;
import com.forge.modules.plugin.license.PluginLicenseService;
import com.forge.modules.plugin.mapper.SysPluginLicenseMapper;
import com.forge.modules.plugin.mapper.SysPluginMapper;
import com.forge.modules.plugin.mapper.SysPluginTenantMapper;
import com.forge.modules.plugin.state.PluginCacheService;
import com.forge.modules.system.service.UserAuthCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 插件管理服务：注册表查询 / 租户级启停（启用硬闸=有效 License）/ 平台总闸。
 *
 * @author forge-admin
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PluginManageService {

    private final SysPluginMapper pluginMapper;

    private final SysPluginTenantMapper tenantPluginMapper;

    private final SysPluginLicenseMapper licenseMapper;

    private final PluginLicenseService licenseService;

    private final PluginCacheService cacheService;

    private final UserAuthCacheService userAuthCacheService;

    /** 插件分页（全局注册表） */
    public PageResult<SysPlugin> pagePlugins(int pageNum, int pageSize, String keyword) {
        Page<SysPlugin> page = new Page<>(pageNum, pageSize);
        Page<SysPlugin> result = pluginMapper.selectPage(page, Wrappers.<SysPlugin>lambdaQuery()
                .and(keyword != null && !keyword.isBlank(),
                        w -> w.like(SysPlugin::getName, keyword).or().like(SysPlugin::getPluginCode, keyword))
                .orderByAsc(SysPlugin::getPluginCode));
        return PageResult.of(result.getRecords(), result.getTotal(), result.getCurrent(), result.getSize());
    }

    /** 全部注册插件（供授权抽屉/下拉） */
    public List<SysPlugin> listPlugins() {
        return pluginMapper.selectList(Wrappers.<SysPlugin>lambdaQuery()
                .orderByAsc(SysPlugin::getPluginCode));
    }

    /** 指定插件全部租户的启停与授权状态（平台管理员视角） */
    public List<SysPluginTenant> listTenantStates(String pluginCode) {
        return tenantPluginMapper.selectList(Wrappers.<SysPluginTenant>lambdaQuery()
                .eq(SysPluginTenant::getPluginCode, pluginCode)
                .orderByDesc(SysPluginTenant::getEnabled));
    }

    /**
     * 启用插件（租户粒度）：未授权不可启用——02 验收 A2 的硬闸。
     */
    @Transactional(rollbackFor = Exception.class)
    public void enable(String pluginCode, Long tenantId) {
        SysPlugin plugin = requirePlugin(pluginCode);
        if (!Integer.valueOf(SysPlugin.GLOBAL_STATUS_ENABLED).equals(plugin.getGlobalStatus())) {
            throw new BusinessException(ResultCode.PLUGIN_GLOBAL_DISABLED, "插件已被平台禁用，无法启用");
        }
        // 收费授权插件：License 硬闸；免费开放插件：跳过（仍需显式启用动作）
        // fail-closed：charge_mode 仅显式等于 FREE 才免闸（null/异常值按收费处理）
        if (!Integer.valueOf(SysPlugin.CHARGE_MODE_FREE).equals(plugin.getChargeMode())
                && !licenseService.hasActiveLicense(tenantId, pluginCode, LocalDateTime.now())) {
            throw new BusinessException(ResultCode.LICENSE_REQUIRED,
                    "租户 " + tenantId + " 对插件[" + pluginCode + "]无有效授权，请先导入 License");
        }
        LocalDateTime now = LocalDateTime.now();
        SysPluginTenant existing = tenantPluginMapper.selectOne(Wrappers.<SysPluginTenant>lambdaQuery()
                .eq(SysPluginTenant::getTenantId, tenantId)
                .eq(SysPluginTenant::getPluginCode, pluginCode)
                .last("LIMIT 1"));
        if (existing == null) {
            SysPluginTenant record = new SysPluginTenant();
            record.setTenantId(tenantId);
            record.setPluginCode(pluginCode);
            record.setEnabled(1);
            record.setEnabledTime(now);
            record.setCreateTime(now);
            record.setUpdateTime(now);
            tenantPluginMapper.insert(record);
        } else {
            // updateById 对 null 字段不更新，清空停用原因须用 UpdateWrapper 显式 set null
            tenantPluginMapper.update(null, Wrappers.<SysPluginTenant>lambdaUpdate()
                    .eq(SysPluginTenant::getId, existing.getId())
                    .set(SysPluginTenant::getEnabled, 1)
                    .set(SysPluginTenant::getEnabledTime, now)
                    .set(SysPluginTenant::getDisabledReason, null)
                    .set(SysPluginTenant::getUpdateTime, now));
        }
        afterStateChanged(tenantId, pluginCode);
        log.info("插件[{}]对租户 {} 启用", pluginCode, tenantId);
    }

    /** 停用插件（菜单隐藏、接口拒绝，数据全量保留——02 验收 A2） */
    @Transactional(rollbackFor = Exception.class)
    public void disable(String pluginCode, Long tenantId, String reason) {
        requirePlugin(pluginCode);
        LocalDateTime now = LocalDateTime.now();
        SysPluginTenant existing = tenantPluginMapper.selectOne(Wrappers.<SysPluginTenant>lambdaQuery()
                .eq(SysPluginTenant::getTenantId, tenantId)
                .eq(SysPluginTenant::getPluginCode, pluginCode)
                .last("LIMIT 1"));
        if (existing != null && Integer.valueOf(1).equals(existing.getEnabled())) {
            SysPluginTenant patch = new SysPluginTenant();
            patch.setId(existing.getId());
            patch.setEnabled(0);
            patch.setDisabledReason(reason);
            patch.setDisabledTime(now);
            patch.setUpdateTime(now);
            tenantPluginMapper.updateById(patch);
        }
        afterStateChanged(tenantId, pluginCode);
        log.info("插件[{}]对租户 {} 停用（原因: {}）", pluginCode, tenantId, reason);
    }

    /**
     * 收费模式切换（平台运营决策；不联动租户启停）：
     * 免费→收费后，已启用但无 License 的租户由每日巡检收口停用（02 确认记录 Q3）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateChargeMode(String pluginCode, int chargeMode) {
        SysPlugin plugin = requirePlugin(pluginCode);
        if (chargeMode != SysPlugin.CHARGE_MODE_FREE && chargeMode != SysPlugin.CHARGE_MODE_LICENSE) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "收费模式非法: " + chargeMode);
        }
        SysPlugin patch = new SysPlugin();
        patch.setId(plugin.getId());
        patch.setChargeMode(chargeMode);
        patch.setUpdateTime(LocalDateTime.now());
        pluginMapper.updateById(patch);
        log.info("插件[{}]收费模式切换为 {}", pluginCode,
                chargeMode == SysPlugin.CHARGE_MODE_FREE ? "免费开放" : "收费授权");
    }

    /** 平台总闸：禁用=全部租户瞬时不可访问（fail-closed，缓存 evict 后立即生效） */
    @Transactional(rollbackFor = Exception.class)
    public void updateGlobalStatus(String pluginCode, int globalStatus) {
        SysPlugin plugin = requirePlugin(pluginCode);
        SysPlugin patch = new SysPlugin();
        patch.setId(plugin.getId());
        patch.setGlobalStatus(globalStatus);
        patch.setUpdateTime(LocalDateTime.now());
        pluginMapper.updateById(patch);
        // 全租户状态缓存失效：按租户启用行逐个 evict + 编码集全 evict
        List<SysPluginTenant> enabledRows = tenantPluginMapper.selectList(Wrappers.<SysPluginTenant>lambdaQuery()
                .eq(SysPluginTenant::getPluginCode, pluginCode));
        for (SysPluginTenant row : enabledRows) {
            cacheService.evictTenantCaches(row.getTenantId(), pluginCode);
        }
        userAuthCacheService.evictAll();
        log.info("插件[{}]平台总闸切换为 {}", pluginCode, globalStatus);
    }

    /** 插件某租户的当前有效 License（列表展示到期信息） */
    public SysPluginLicense getActiveLicense(Long tenantId, String pluginCode) {
        return licenseMapper.selectOne(Wrappers.<SysPluginLicense>lambdaQuery()
                .eq(SysPluginLicense::getTenantId, tenantId)
                .eq(SysPluginLicense::getPluginCode, pluginCode)
                .eq(SysPluginLicense::getStatus, SysPluginLicense.STATUS_ACTIVE)
                .le(SysPluginLicense::getNotBefore, LocalDateTime.now())
                .gt(SysPluginLicense::getNotAfter, LocalDateTime.now())
                .last("LIMIT 1"));
    }

    // ===== 内部 =====

    private SysPlugin requirePlugin(String pluginCode) {
        SysPlugin plugin = pluginMapper.selectOne(Wrappers.<SysPlugin>lambdaQuery()
                .eq(SysPlugin::getPluginCode, pluginCode)
                .last("LIMIT 1"));
        if (plugin == null) {
            throw new BusinessException(ResultCode.PLUGIN_NOT_FOUND, "插件不存在或未注册: " + pluginCode);
        }
        return plugin;
    }

    /** 状态变更后：状态/scope/编码集/菜单/用户认证缓存全链失效 */
    private void afterStateChanged(Long tenantId, String pluginCode) {
        cacheService.evictTenantCaches(tenantId, pluginCode);
        userAuthCacheService.evictAll();
    }
}
