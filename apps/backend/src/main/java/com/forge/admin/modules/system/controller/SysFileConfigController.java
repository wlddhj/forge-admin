package com.forge.admin.modules.system.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.forge.admin.common.annotation.OperationLog;
import com.forge.admin.common.response.PageResult;
import com.forge.admin.common.response.Result;
import com.forge.admin.modules.system.dto.file.FileConfigQueryRequest;
import com.forge.admin.modules.system.dto.file.FileConfigRequest;
import com.forge.admin.modules.system.dto.file.FileConfigResponse;
import com.forge.admin.modules.system.service.SysFileConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 文件存储配置控制器
 *
 * @author standadmin
 */
@Slf4j
@Tag(name = "文件存储配置")
@RestController
@RequestMapping("/system/file-config")
@RequiredArgsConstructor
public class SysFileConfigController {

    private final SysFileConfigService sysFileConfigService;

    @Operation(summary = "分页查询文件存储配置")
    @GetMapping("/list")
    @PreAuthorize("hasAuthority('system:file-config:list')")
    public Result<PageResult<FileConfigResponse>> list(FileConfigQueryRequest request) {
        Page<FileConfigResponse> page = sysFileConfigService.pageFileConfig(request);
        PageResult<FileConfigResponse> result = PageResult.of(
                page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize()
        );
        return Result.success(result);
    }

    @Operation(summary = "获取文件存储配置详情")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('system:file-config:query')")
    public Result<FileConfigResponse> getInfo(@PathVariable Long id) {
        return Result.success(sysFileConfigService.getFileConfigDetail(id));
    }

    @Operation(summary = "新增文件存储配置")
    @PostMapping
    @PreAuthorize("hasAuthority('system:file-config:add')")
    @OperationLog(title = "文件存储配置", businessType = OperationLog.BusinessType.INSERT)
    public Result<Void> add(@Valid @RequestBody FileConfigRequest request) {
        sysFileConfigService.addFileConfig(request);
        return Result.success();
    }

    @Operation(summary = "更新文件存储配置")
    @PutMapping
    @PreAuthorize("hasAuthority('system:file-config:edit')")
    @OperationLog(title = "文件存储配置", businessType = OperationLog.BusinessType.UPDATE)
    public Result<Void> update(@Valid @RequestBody FileConfigRequest request) {
        sysFileConfigService.updateFileConfig(request);
        return Result.success();
    }

    @Operation(summary = "删除文件存储配置")
    @DeleteMapping
    @PreAuthorize("hasAuthority('system:file-config:delete')")
    @OperationLog(title = "文件存储配置", businessType = OperationLog.BusinessType.DELETE)
    public Result<Void> delete(@RequestBody List<Long> ids) {
        sysFileConfigService.deleteFileConfig(ids);
        return Result.success();
    }

    @Operation(summary = "设置默认配置")
    @PutMapping("/{id}/default")
    @PreAuthorize("hasAuthority('system:file-config:edit')")
    @OperationLog(title = "文件存储配置", businessType = OperationLog.BusinessType.UPDATE)
    public Result<Void> setDefault(@PathVariable Long id) {
        sysFileConfigService.setDefaultConfig(id);
        return Result.success();
    }

    @Operation(summary = "更新配置状态")
    @PutMapping("/{id}/status")
    @PreAuthorize("hasAuthority('system:file-config:edit')")
    @OperationLog(title = "文件存储配置", businessType = OperationLog.BusinessType.UPDATE)
    public Result<Void> updateStatus(@PathVariable Long id, @RequestParam Integer status) {
        sysFileConfigService.updateStatus(id, status);
        return Result.success();
    }

    @Operation(summary = "测试连接")
    @PutMapping("/{id}/conn")
    @PreAuthorize("hasAuthority('system:file-config:list')")
    public Result<Void> testConnection(@PathVariable Long id) {
        sysFileConfigService.testConnection(id);
        return Result.success();
    }
}
