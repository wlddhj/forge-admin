package com.forge.modules.plugin.controller.admin;

import com.forge.common.response.PageResult;
import com.forge.common.response.Result;
import com.forge.common.utils.UserContext;
import com.forge.framework.plugin.descriptor.PluginDescriptor;
import com.forge.framework.plugin.menu.PluginMenuFilter;
import com.forge.framework.web.annotation.OperationLog;
import com.forge.modules.plugin.dto.TenantPluginVO;
import com.forge.modules.plugin.data.PluginScopeChecker;
import com.forge.modules.plugin.entity.SysPlugin;
import com.forge.modules.plugin.entity.SysPluginLicense;
import com.forge.modules.plugin.entity.SysPluginTenant;
import com.forge.modules.plugin.license.PluginLicenseService;
import com.forge.modules.plugin.service.PluginManageService;
import com.forge.modules.system.entity.SysTenant;
import com.forge.modules.system.service.SysTenantService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 插件管理（平台管理员；plugin:list 按角色控制可见性）
 *
 * @author forge-admin
 */
@Tag(name = "插件管理")
@RestController
@RequestMapping("/plugin")
@RequiredArgsConstructor
public class PluginController {

    private static final int EXPIRING_SOON_DAYS = 7;

    private final PluginManageService manageService;

    private final PluginLicenseService licenseService;

    private final PluginScopeChecker scopeChecker;

    private final SysTenantService sysTenantService;

    private final PluginMenuFilter stateService;

    private final List<PluginDescriptor> descriptors;

    @Operation(summary = "插件分页")
    @GetMapping("/page")
    @PreAuthorize("hasAuthority('plugin:list')")
    public Result<PageResult<SysPlugin>> page(@RequestParam(defaultValue = "1") int pageNum,
                                              @RequestParam(defaultValue = "20") int pageSize,
                                              @RequestParam(required = false) String keyword) {
        return Result.success(manageService.pagePlugins(pageNum, pageSize, keyword));
    }

    @Operation(summary = "全部注册插件")
    @GetMapping("/list")
    @PreAuthorize("hasAuthority('plugin:list')")
    public Result<List<SysPlugin>> list() {
        return Result.success(manageService.listPlugins());
    }

    @Operation(summary = "插件租户明细（启停/授权/到期）")
    @GetMapping("/{pluginCode}/tenants")
    @PreAuthorize("hasAuthority('plugin:list')")
    public Result<List<TenantPluginVO>> tenants(@PathVariable String pluginCode) {
        List<SysPluginTenant> states = manageService.listTenantStates(pluginCode);
        Map<Long, String> tenantNames = sysTenantService.list().stream()
                .collect(Collectors.toMap(SysTenant::getId, SysTenant::getName, (first, ignored) -> first));
        List<TenantPluginVO> result = new ArrayList<>();
        for (SysPluginTenant state : states) {
            SysPluginLicense active = manageService.getActiveLicense(state.getTenantId(), pluginCode);
            TenantPluginVO.TenantPluginVOBuilder builder = TenantPluginVO.builder()
                    .tenantId(state.getTenantId())
                    .tenantName(tenantNames.get(state.getTenantId()))
                    .enabled(state.getEnabled())
                    .disabledReason(state.getDisabledReason())
                    .enabledTime(state.getEnabledTime())
                    .disabledTime(state.getDisabledTime())
                    .effectiveScopes(scopeChecker.effectiveScopes(pluginCode, state.getTenantId()));
            if (active != null) {
                builder.licenseKey(active.getLicenseKey())
                        .licensee(active.getLicenseeName())
                        .notAfter(active.getNotAfter())
                        .expiringSoon(active.getNotAfter().isBefore(LocalDateTime.now().plusDays(EXPIRING_SOON_DAYS)));
            } else {
                builder.expiringSoon(false);
            }
            result.add(builder.build());
        }
        return Result.success(result);
    }

    @Operation(summary = "启用插件（租户粒度，未授权不可启用）")
    @PostMapping("/{pluginCode}/tenants/{tenantId}/enable")
    @PreAuthorize("hasAuthority('plugin:enable')")
    @OperationLog(title = "插件管理", businessType = OperationLog.BusinessType.UPDATE)
    public Result<Void> enable(@PathVariable String pluginCode, @PathVariable Long tenantId) {
        manageService.enable(pluginCode, tenantId);
        return Result.success();
    }

    @Operation(summary = "停用插件（菜单隐藏，数据保留）")
    @PostMapping("/{pluginCode}/tenants/{tenantId}/disable")
    @PreAuthorize("hasAuthority('plugin:disable')")
    @OperationLog(title = "插件管理", businessType = OperationLog.BusinessType.UPDATE)
    public Result<Void> disable(@PathVariable String pluginCode, @PathVariable Long tenantId) {
        manageService.disable(pluginCode, tenantId, "手动停用");
        return Result.success();
    }

    @Operation(summary = "平台总闸（禁用=全部租户瞬时不可访问）")
    @PutMapping("/{pluginCode}/global-status")
    @PreAuthorize("hasAuthority('plugin:global')")
    @OperationLog(title = "插件管理", businessType = OperationLog.BusinessType.UPDATE)
    public Result<Void> updateGlobalStatus(@PathVariable String pluginCode, @RequestParam int globalStatus) {
        manageService.updateGlobalStatus(pluginCode, globalStatus);
        return Result.success();
    }

    @Operation(summary = "本租户启用插件（租户管理员视角，只读）")
    @GetMapping("/my")
    public Result<List<SysPlugin>> my() {
        UserContext context = UserContext.get();
        if (context == null || context.getTenantId() == null) {
            return Result.success(List.of());
        }
        Set<String> enabledCodes = stateService.getEnabledPluginCodes(context.getTenantId());
        if (enabledCodes.isEmpty()) {
            return Result.success(List.of());
        }
        List<SysPlugin> result = manageService.listPlugins().stream()
                .filter(p -> enabledCodes.contains(p.getPluginCode()))
                .toList();
        return Result.success(result);
    }
}
