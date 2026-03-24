package com.forge.admin.modules.system.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.forge.admin.common.annotation.OperationLog;
import com.forge.admin.common.response.PageResult;
import com.forge.admin.common.response.Result;
import com.forge.admin.modules.system.dto.config.ConfigQueryRequest;
import com.forge.admin.modules.system.dto.config.ConfigRequest;
import com.forge.admin.modules.system.dto.config.ConfigResponse;
import com.forge.admin.modules.system.service.SysConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "系统配置管理")
@RestController
@RequestMapping("/system/config")
@RequiredArgsConstructor
public class SysConfigController {

    private final SysConfigService sysConfigService;

    @Operation(summary = "分页查询配置")
    @GetMapping("/list")
    @PreAuthorize("hasAuthority('system:config:list')")
    public Result<PageResult<ConfigResponse>> list(ConfigQueryRequest request) {
        Page<ConfigResponse> page = sysConfigService.pageConfigs(request);
        PageResult<ConfigResponse> result = PageResult.of(
                page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize()
        );
        return Result.success(result);
    }

    @Operation(summary = "根据键获取配置值")
    @GetMapping("/key/{configKey}")
    public Result<String> getByKey(@PathVariable String configKey) {
        return Result.success(sysConfigService.getConfigValueByKey(configKey));
    }

    @Operation(summary = "获取配置详情")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('system:config:query')")
    public Result<ConfigResponse> getInfo(@PathVariable Long id) {
        return Result.success(sysConfigService.getConfigDetail(id));
    }

    @Operation(summary = "新增配置")
    @PostMapping
    @PreAuthorize("hasAuthority('system:config:add')")
    @OperationLog(title = "系统配置管理", businessType = OperationLog.BusinessType.INSERT)
    public Result<Void> add(@Valid @RequestBody ConfigRequest request) {
        sysConfigService.addConfig(request);
        return Result.success();
    }

    @Operation(summary = "更新配置")
    @PutMapping
    @PreAuthorize("hasAuthority('system:config:edit')")
    @OperationLog(title = "系统配置管理", businessType = OperationLog.BusinessType.UPDATE)
    public Result<Void> edit(@Valid @RequestBody ConfigRequest request) {
        sysConfigService.updateConfig(request);
        return Result.success();
    }

    @Operation(summary = "删除配置")
    @DeleteMapping("/{ids}")
    @PreAuthorize("hasAuthority('system:config:delete')")
    @OperationLog(title = "系统配置管理", businessType = OperationLog.BusinessType.DELETE)
    public Result<Void> delete(@PathVariable List<Long> ids) {
        sysConfigService.deleteConfigs(ids);
        return Result.success();
    }
}
