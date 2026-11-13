package com.forge.modules.plugin.license;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * License 到期巡检任务（Quartz 每日调度，sys_job 记录随升级 SQL 注册）：
 * 过期授权置失效并自动停用对应租户插件（disabled_reason=license_expired），
 * 联动清缓存。Quartz 线程无租户上下文，操作的全局表不受租户插件影响。
 *
 * <p>invokeTarget 形如：pluginLicenseCheckTask.execute
 *
 * @author forge-admin
 */
@Slf4j
@Component("pluginLicenseCheckTask")
@RequiredArgsConstructor
public class PluginLicenseCheckTask {

    private final PluginLicenseService licenseService;

    public void execute() {
        int disabled = licenseService.expireOverdueLicenses();
        if (disabled > 0) {
            log.info("License 巡检: {} 个租户插件因授权到期自动停用", disabled);
        }
    }
}
