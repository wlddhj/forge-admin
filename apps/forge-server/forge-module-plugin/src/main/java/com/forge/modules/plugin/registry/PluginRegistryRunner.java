package com.forge.modules.plugin.registry;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.forge.framework.plugin.descriptor.PluginDescriptor;
import com.forge.modules.plugin.entity.SysPlugin;
import com.forge.modules.plugin.mapper.SysPluginMapper;
import com.forge.modules.system.entity.SysOperationLog;
import com.forge.modules.system.mapper.SysOperationLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 插件注册 Runner：启动时扫描全部 {@link PluginDescriptor} Bean，
 * upsert sys_plugin（版本变化记操作日志，无人工确认环节——02 设计 Q6 决策），
 * 并触发菜单 diff 同步。单插件失败不影响其他插件。
 *
 * @author forge-admin
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PluginRegistryRunner implements ApplicationRunner {

    private static final Pattern SEMVER = Pattern.compile("^\\d+\\.\\d+\\.\\d+(-[0-9A-Za-z.-]+)?$");

    private final List<PluginDescriptor> descriptors;

    private final SysPluginMapper sysPluginMapper;

    private final PluginMenuSyncService pluginMenuSyncService;

    private final SysOperationLogMapper sysOperationLogMapper;

    @Override
    public void run(ApplicationArguments args) {
        if (descriptors == null || descriptors.isEmpty()) {
            log.info("无插件描述符，跳过插件注册");
            return;
        }
        for (PluginDescriptor descriptor : descriptors) {
            try {
                register(descriptor);
            } catch (Exception e) {
                log.error("插件[{}]注册失败（不影响其他插件与底座启动）", descriptor.pluginCode(), e);
            }
        }
    }

    public void register(PluginDescriptor descriptor) {
        String pluginCode = descriptor.pluginCode();
        if (pluginCode == null || pluginCode.isBlank()) {
            throw new IllegalArgumentException("插件编码为空");
        }
        if (descriptor.version() == null || !SEMVER.matcher(descriptor.version()).matches()) {
            throw new IllegalArgumentException("插件[" + pluginCode + "]版本号非法（须 SemVer）: " + descriptor.version());
        }

        SysPlugin existing = sysPluginMapper.selectOne(Wrappers.<SysPlugin>lambdaQuery()
                .eq(SysPlugin::getPluginCode, pluginCode)
                .last("LIMIT 1"));
        Long pluginId;
        if (existing == null) {
            SysPlugin plugin = new SysPlugin();
            plugin.setPluginCode(pluginCode);
            plugin.setName(descriptor.name());
            plugin.setDescription(descriptor.description());
            plugin.setProvider(descriptor.provider());
            plugin.setRunningVersion(descriptor.version());
            plugin.setGlobalStatus(SysPlugin.GLOBAL_STATUS_ENABLED);
            // 收费模式默认值仅首次注册写入（此后管理端设置为准，升级不覆盖）
            plugin.setChargeMode(descriptor.freeOpen()
                    ? SysPlugin.CHARGE_MODE_FREE : SysPlugin.CHARGE_MODE_LICENSE);
            plugin.setMenusSynced(0);
            plugin.setCreateTime(LocalDateTime.now());
            plugin.setUpdateTime(LocalDateTime.now());
            sysPluginMapper.insert(plugin);
            pluginId = plugin.getId();
            log.info("插件[{}]注册成功: 版本 {}，提供方 {}", pluginCode, descriptor.version(), descriptor.provider());
        } else {
            pluginId = existing.getId();
            boolean changed = !descriptor.version().equals(existing.getRunningVersion());
            SysPlugin patch = new SysPlugin();
            patch.setId(pluginId);
            patch.setName(descriptor.name());
            patch.setDescription(descriptor.description());
            patch.setProvider(descriptor.provider());
            patch.setRunningVersion(descriptor.version());
            patch.setUpdateTime(LocalDateTime.now());
            sysPluginMapper.updateById(patch);
            if (changed) {
                log.info("插件[{}]版本变化: {} → {}", pluginCode, existing.getRunningVersion(), descriptor.version());
                recordVersionChangeLog(pluginCode, existing.getRunningVersion(), descriptor.version());
            }
        }

        // 菜单 diff 同步（幂等，每次启动执行；独立事务，见 PluginMenuSyncService）
        pluginMenuSyncService.syncMenus(descriptor);

        SysPlugin syncedPatch = new SysPlugin();
        syncedPatch.setId(pluginId);
        syncedPatch.setMenusSynced(1);
        syncedPatch.setUpdateTime(LocalDateTime.now());
        sysPluginMapper.updateById(syncedPatch);
    }

    /** 版本变化留痕（知情与审计由操作日志承载） */
    private void recordVersionChangeLog(String pluginCode, String from, String to) {
        SysOperationLog operationLog = new SysOperationLog();
        operationLog.setTitle("插件注册");
        operationLog.setBusinessType("UPDATE");
        operationLog.setRequestMethod("SYSTEM");
        operationLog.setRequestUrl("plugin/registry/" + pluginCode);
        operationLog.setOperatorName("system");
        operationLog.setRequestParam("{\"pluginCode\":\"" + pluginCode + "\",\"from\":\"" + from + "\",\"to\":\"" + to + "\"}");
        operationLog.setStatus(200);
        operationLog.setOperateTime(LocalDateTime.now());
        operationLog.setCostTime(0L);
        try {
            sysOperationLogMapper.insert(operationLog);
        } catch (Exception e) {
            // 审计失败不阻断注册
            log.warn("插件[{}]版本变更操作日志写入失败", pluginCode, e);
        }
    }
}
