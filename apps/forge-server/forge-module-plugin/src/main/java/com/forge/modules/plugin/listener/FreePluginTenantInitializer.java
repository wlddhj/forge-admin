package com.forge.modules.plugin.listener;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.forge.modules.plugin.entity.SysPlugin;
import com.forge.modules.plugin.entity.SysPluginTenant;
import com.forge.modules.plugin.mapper.SysPluginMapper;
import com.forge.modules.plugin.mapper.SysPluginTenantMapper;
import com.forge.modules.system.event.TenantCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDateTime;

/**
 * 新租户免费插件自动启用：租户创建事务提交后，为该租户启用全部
 * charge_mode=FREE 且平台总闸正常的插件（disabled_reason=free_auto 标记来源）。
 *
 * <p>AFTER_COMMIT + fallbackExecution（非事务发布场景亦可触发）；
 * 失败仅记 ERROR 不影响租户创建结果（缺失的免费启用可人工补开）。
 *
 * @author forge-admin
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FreePluginTenantInitializer {

    public static final String REASON_FREE_AUTO = "free_auto";

    private final SysPluginMapper pluginMapper;

    private final SysPluginTenantMapper tenantPluginMapper;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onTenantCreated(TenantCreatedEvent event) {
        try {
            LocalDateTime now = LocalDateTime.now();
            var freePlugins = pluginMapper.selectList(Wrappers.<SysPlugin>lambdaQuery()
                    .eq(SysPlugin::getChargeMode, SysPlugin.CHARGE_MODE_FREE)
                    .eq(SysPlugin::getGlobalStatus, SysPlugin.GLOBAL_STATUS_ENABLED));
            for (SysPlugin plugin : freePlugins) {
                Long exists = tenantPluginMapper.selectCount(Wrappers.<SysPluginTenant>lambdaQuery()
                        .eq(SysPluginTenant::getTenantId, event.tenantId())
                        .eq(SysPluginTenant::getPluginCode, plugin.getPluginCode()));
                if (exists > 0) {
                    continue;
                }
                SysPluginTenant record = new SysPluginTenant();
                record.setTenantId(event.tenantId());
                record.setPluginCode(plugin.getPluginCode());
                record.setEnabled(1);
                record.setDisabledReason(REASON_FREE_AUTO);
                record.setEnabledTime(now);
                record.setCreateTime(now);
                record.setUpdateTime(now);
                tenantPluginMapper.insert(record);
            }
            if (!freePlugins.isEmpty()) {
                log.info("新租户 {} 自动启用免费插件 {} 个", event.tenantId(), freePlugins.size());
            }
        } catch (Exception e) {
            log.error("新租户 {} 免费插件自动启用失败（不影响租户创建，可人工补开）", event.tenantId(), e);
        }
    }
}
