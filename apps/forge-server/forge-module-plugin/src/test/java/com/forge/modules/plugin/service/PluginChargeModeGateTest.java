package com.forge.modules.plugin.service;

import com.forge.common.exception.BusinessException;
import com.forge.common.response.ResultCode;
import com.forge.modules.plugin.entity.SysPlugin;
import com.forge.modules.plugin.entity.SysPluginTenant;
import com.forge.modules.plugin.license.PluginLicenseService;
import com.forge.modules.plugin.mapper.SysPluginLicenseMapper;
import com.forge.modules.plugin.mapper.SysPluginMapper;
import com.forge.modules.plugin.mapper.SysPluginTenantMapper;
import com.forge.modules.plugin.state.PluginCacheService;
import com.forge.modules.system.service.UserAuthCacheService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 收费模式启用闸分支测试：免费插件跳过 License 硬闸 / 收费插件照旧。
 *
 * @author forge-admin
 */
@DisplayName("收费模式启用闸")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PluginChargeModeGateTest {

    @Mock
    private SysPluginMapper pluginMapper;

    @Mock
    private SysPluginTenantMapper tenantPluginMapper;

    @Mock
    private SysPluginLicenseMapper licenseMapper;

    @Mock
    private PluginLicenseService licenseService;

    @Mock
    private PluginCacheService cacheService;

    @Mock
    private UserAuthCacheService userAuthCacheService;

    private PluginManageService service;

    @BeforeEach
    void setup() {
        service = new PluginManageService(pluginMapper, tenantPluginMapper, licenseMapper,
                licenseService, cacheService, userAuthCacheService);
        lenient().when(tenantPluginMapper.insert(any(SysPluginTenant.class))).thenReturn(1);
        // 无既有启用行 → enable 走 insert 分支
        lenient().when(tenantPluginMapper.selectOne(any())).thenReturn(null);
    }

    private SysPlugin plugin(int chargeMode) {
        SysPlugin plugin = new SysPlugin();
        plugin.setId(1L);
        plugin.setPluginCode("demo");
        plugin.setGlobalStatus(SysPlugin.GLOBAL_STATUS_ENABLED);
        plugin.setChargeMode(chargeMode);
        return plugin;
    }

    @Test
    @DisplayName("免费插件：无任何 License 也可启用（不查授权）")
    void freePluginEnablesWithoutLicense() {
        when(pluginMapper.selectOne(any())).thenReturn(plugin(SysPlugin.CHARGE_MODE_FREE));

        service.enable("demo", 9L);

        verify(tenantPluginMapper).insert(any(SysPluginTenant.class));
        verify(licenseService, never()).hasActiveLicense(anyLong(), anyString(), any());
    }

    @Test
    @DisplayName("收费插件：无有效 License 拒绝（LICENSE_REQUIRED）")
    void chargedPluginRejectedWithoutLicense() {
        when(pluginMapper.selectOne(any())).thenReturn(plugin(SysPlugin.CHARGE_MODE_LICENSE));
        when(licenseService.hasActiveLicense(eq(9L), eq("demo"), any())).thenReturn(false);

        assertThatThrownBy(() -> service.enable("demo", 9L))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.LICENSE_REQUIRED.getCode()));
        verify(tenantPluginMapper, never()).insert(any(SysPluginTenant.class));
    }

    @Test
    @DisplayName("收费插件：有有效 License 正常启用")
    void chargedPluginEnablesWithLicense() {
        when(pluginMapper.selectOne(any())).thenReturn(plugin(SysPlugin.CHARGE_MODE_LICENSE));
        when(licenseService.hasActiveLicense(eq(9L), eq("demo"), any())).thenReturn(true);

        service.enable("demo", 9L);

        verify(tenantPluginMapper).insert(any(SysPluginTenant.class));
    }

    @Test
    @DisplayName("模式切换：非法值拒绝；合法值更新且不联动租户启停")
    void chargeModeSwitch() {
        when(pluginMapper.selectOne(any())).thenReturn(plugin(SysPlugin.CHARGE_MODE_LICENSE));

        assertThatThrownBy(() -> service.updateChargeMode("demo", 5))
                .isInstanceOf(BusinessException.class);

        service.updateChargeMode("demo", SysPlugin.CHARGE_MODE_FREE);
        verify(pluginMapper).updateById(any(SysPlugin.class));
        verify(tenantPluginMapper, never()).update(any(), any());
    }

    @Test
    @DisplayName("免费自动启用的行带 free_auto 标记来源（监听器行为在独立测试）")
    void enableRecordsNoReasonForManualEnable() {
        when(pluginMapper.selectOne(any())).thenReturn(plugin(SysPlugin.CHARGE_MODE_FREE));
        service.enable("demo", 9L);
        ArgumentCaptor<SysPluginTenant> captor = ArgumentCaptor.forClass(SysPluginTenant.class);
        verify(tenantPluginMapper).insert(captor.capture());
        assertThat(captor.getValue().getEnabled()).isEqualTo(1);
        // 手动启用不带 free_auto 标记（区别于新租户自动启用）
        assertThat(captor.getValue().getDisabledReason()).isNull();
    }
}
