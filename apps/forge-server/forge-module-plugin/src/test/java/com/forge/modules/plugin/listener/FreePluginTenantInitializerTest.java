package com.forge.modules.plugin.listener;

import com.forge.modules.plugin.entity.SysPlugin;
import com.forge.modules.plugin.entity.SysPluginTenant;
import com.forge.modules.plugin.mapper.SysPluginMapper;
import com.forge.modules.plugin.mapper.SysPluginTenantMapper;
import com.forge.modules.system.event.TenantCreatedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 新租户免费插件自动启用测试：只启用免费+总闸正常插件，幂等（已有行跳过），失败不外抛。
 *
 * @author forge-admin
 */
@DisplayName("新租户免费插件自动启用")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FreePluginTenantInitializerTest {

    @Mock
    private SysPluginMapper pluginMapper;

    @Mock
    private SysPluginTenantMapper tenantPluginMapper;

    @InjectMocks
    private FreePluginTenantInitializer initializer;

    private SysPlugin plugin(String code, int chargeMode, int globalStatus) {
        SysPlugin plugin = new SysPlugin();
        plugin.setPluginCode(code);
        plugin.setChargeMode(chargeMode);
        plugin.setGlobalStatus(globalStatus);
        return plugin;
    }

    @Test
    @DisplayName("自动启用查询命中的免费插件（SQL 条件=免费+总闸正常），并带 free_auto 标记")
    void enablesFreePluginsOnly() {
        // mock 语义 = mapper 契约：selectList 已按 charge_mode=FREE AND global_status=1 过滤
        when(pluginMapper.selectList(any())).thenReturn(List.of(
                plugin("free-a", SysPlugin.CHARGE_MODE_FREE, SysPlugin.GLOBAL_STATUS_ENABLED)));
        when(tenantPluginMapper.selectCount(any())).thenReturn(0L);

        initializer.onTenantCreated(new TenantCreatedEvent(99L));

        ArgumentCaptor<SysPluginTenant> captor = ArgumentCaptor.forClass(SysPluginTenant.class);
        verify(tenantPluginMapper).insert(captor.capture());
        assertThat(captor.getValue().getPluginCode()).isEqualTo("free-a");
        assertThat(captor.getValue().getTenantId()).isEqualTo(99L);
        assertThat(captor.getValue().getEnabled()).isEqualTo(1);
        assertThat(captor.getValue().getDisabledReason())
                .isEqualTo(FreePluginTenantInitializer.REASON_FREE_AUTO);
    }

    @Test
    @DisplayName("已有启用行的插件跳过（幂等）")
    void skipsExistingRows() {
        when(pluginMapper.selectList(any())).thenReturn(List.of(
                plugin("free-a", SysPlugin.CHARGE_MODE_FREE, SysPlugin.GLOBAL_STATUS_ENABLED)));
        when(tenantPluginMapper.selectCount(any())).thenReturn(1L);

        initializer.onTenantCreated(new TenantCreatedEvent(99L));

        verify(tenantPluginMapper, never()).insert(any(SysPluginTenant.class));
    }

    @Test
    @DisplayName("监听失败不外抛（不影响租户创建结果）")
    void swallowsExceptions() {
        when(pluginMapper.selectList(any())).thenThrow(new IllegalStateException("db down"));
        // 不抛异常即通过
        initializer.onTenantCreated(new TenantCreatedEvent(99L));
    }
}
