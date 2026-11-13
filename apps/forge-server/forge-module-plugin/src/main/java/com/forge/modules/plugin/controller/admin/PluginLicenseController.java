package com.forge.modules.plugin.controller.admin;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.forge.common.response.PageResult;
import com.forge.common.response.Result;
import com.forge.framework.web.annotation.OperationLog;
import com.forge.framework.web.annotation.RepeatSubmit;
import com.forge.modules.plugin.dto.LicenseIssueRequest;
import com.forge.modules.plugin.entity.SysPluginLicense;
import com.forge.modules.plugin.license.PluginLicenseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 插件 License 管理（导入/列表/撤销）
 *
 * @author forge-admin
 */
@Tag(name = "插件License管理")
@RestController
@RequestMapping("/plugin/license")
@RequiredArgsConstructor
public class PluginLicenseController {

    private final PluginLicenseService licenseService;

    @Operation(summary = "导入 License 文件（一码多插件原子导入）")
    @PostMapping("/import")
    @PreAuthorize("hasAuthority('plugin:license:import')")
    @RepeatSubmit
    @OperationLog(title = "插件License管理", businessType = OperationLog.BusinessType.IMPORT)
    public Result<Map<String, Object>> importLicense(@RequestParam("file") MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            return Result.failed("License 文件为空");
        }
        String content = new String(file.getBytes(), StandardCharsets.UTF_8);
        int imported = licenseService.importLicense(content);
        return Result.success(Map.of("imported", imported));
    }

    @Operation(summary = "License 分页")
    @GetMapping("/page")
    @PreAuthorize("hasAuthority('plugin:list')")
    public Result<PageResult<SysPluginLicense>> page(@RequestParam(defaultValue = "1") int pageNum,
                                                     @RequestParam(defaultValue = "20") int pageSize,
                                                     @RequestParam(required = false) Long tenantId,
                                                     @RequestParam(required = false) String pluginCode,
                                                     @RequestParam(required = false) Integer status) {
        Page<SysPluginLicense> result = licenseService.pageLicenses(pageNum, pageSize, tenantId, pluginCode, status);
        return Result.success(PageResult.of(result.getRecords(), result.getTotal(), result.getCurrent(), result.getSize()));
    }

    @Operation(summary = "撤销 License（联动停用对应租户插件）")
    @PostMapping("/{id}/revoke")
    @PreAuthorize("hasAuthority('plugin:license:revoke')")
    @OperationLog(title = "插件License管理", businessType = OperationLog.BusinessType.DELETE)
    public Result<Void> revoke(@PathVariable Long id) {
        licenseService.revoke(id);
        return Result.success();
    }

    @Operation(summary = "签发表单元数据（能力开关+租户+插件声明集；登录即可，前端据此隐藏签发入口）")
    @GetMapping("/issue/form-data")
    public Result<Map<String, Object>> issueFormData() {
        Map<String, Object> data = licenseService.buildIssueFormData();
        return Result.success(data);
    }

    @Operation(summary = "在线签发（activate=true 签发并落库生效）")
    @PostMapping("/issue")
    @PreAuthorize("hasAuthority('plugin:license:issue')")
    @RepeatSubmit
    @OperationLog(title = "插件License管理", businessType = OperationLog.BusinessType.INSERT)
    public Result<Map<String, Object>> issue(@Valid @RequestBody LicenseIssueRequest request) {
        return Result.success(licenseService.issue(request));
    }
}
