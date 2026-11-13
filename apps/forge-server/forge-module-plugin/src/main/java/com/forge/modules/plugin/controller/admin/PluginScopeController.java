package com.forge.modules.plugin.controller.admin;

import com.forge.common.response.Result;
import com.forge.framework.plugin.descriptor.PluginDescriptor;
import com.forge.framework.plugin.descriptor.ScopeDefinition;
import com.forge.framework.web.annotation.OperationLog;
import com.forge.modules.plugin.data.PluginScopeChecker;
import com.forge.modules.plugin.license.PluginLicenseService;
import com.forge.modules.plugin.dto.PluginScopeVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 插件数据 scope 管理（勾选式授予：声明集全量 + 授予态 + 来源）
 *
 * @author forge-admin
 */
@Tag(name = "插件数据权限")
@RestController
@RequestMapping("/plugin/scope")
@RequiredArgsConstructor
public class PluginScopeController {

    private final PluginScopeChecker scopeChecker;

    private final PluginLicenseService licenseService;

    private final List<PluginDescriptor> descriptors;

    @Operation(summary = "scope 列表（插件声明集 + 授予态 + 来源）")
    @GetMapping("/list")
    @PreAuthorize("hasAuthority('plugin:list')")
    public Result<List<PluginScopeVO>> list(@RequestParam Long tenantId, @RequestParam String pluginCode) {
        Set<String> adminGranted = scopeChecker.grantedByAdmin(tenantId, pluginCode);
        Set<String> licenseScopes = licenseService.getActiveLicenseScopes(tenantId, pluginCode, LocalDateTime.now());
        List<PluginScopeVO> result = new ArrayList<>();
        for (PluginDescriptor descriptor : descriptors) {
            if (!descriptor.pluginCode().equals(pluginCode)) {
                continue;
            }
            for (ScopeDefinition definition : descriptor.declaredScopes()) {
                String scope = definition.scope();
                boolean byDefault = definition.grantedByDefault();
                boolean byAdmin = adminGranted.contains(scope);
                boolean byLicense = !byDefault && !byAdmin && licenseScopes.contains(scope);
                result.add(PluginScopeVO.builder()
                        .scope(scope)
                        .name(definition.name())
                        .description(definition.description())
                        .granted(byDefault || byAdmin || byLicense)
                        .grantedByDefault(byDefault)
                        .source(byDefault ? "default" : byAdmin ? "admin" : byLicense ? "license" : "none")
                        .build());
            }
        }
        return Result.success(result);
    }

    @Operation(summary = "授予 scope（勾选）")
    @PostMapping("/grant")
    @PreAuthorize("hasAuthority('plugin:scope:grant')")
    @OperationLog(title = "插件数据权限", businessType = OperationLog.BusinessType.GRANT)
    public Result<Void> grant(@RequestParam Long tenantId, @RequestParam String pluginCode, @RequestParam String scope) {
        scopeChecker.grant(tenantId, pluginCode, scope);
        return Result.success();
    }

    @Operation(summary = "回收 scope（取消勾选）")
    @PostMapping("/revoke")
    @PreAuthorize("hasAuthority('plugin:scope:grant')")
    @OperationLog(title = "插件数据权限", businessType = OperationLog.BusinessType.GRANT)
    public Result<Void> revoke(@RequestParam Long tenantId, @RequestParam String pluginCode, @RequestParam String scope) {
        scopeChecker.revoke(tenantId, pluginCode, scope);
        return Result.success();
    }
}
