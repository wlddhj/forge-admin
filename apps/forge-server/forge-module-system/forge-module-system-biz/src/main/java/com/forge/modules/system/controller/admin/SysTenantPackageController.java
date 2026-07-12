package com.forge.modules.system.controller.admin;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.forge.common.response.PageResult;
import com.forge.common.response.Result;
import com.forge.framework.tenant.core.context.TenantContextHolder;
import com.forge.framework.web.annotation.OperationLog;
import com.forge.modules.system.dto.tenant.TenantPackageQueryRequest;
import com.forge.modules.system.dto.tenant.TenantPackageRequest;
import com.forge.modules.system.dto.tenant.TenantPackageResponse;
import com.forge.modules.system.service.SysTenantPackageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 租户套餐管理控制器
 *
 * @author standadmin
 */
@Tag(name = "租户套餐管理")
@RestController
@RequestMapping("/system/tenant-package")
@RequiredArgsConstructor
public class SysTenantPackageController {

    private final SysTenantPackageService packageService;

    @Operation(summary = "分页查询租户套餐")
    @GetMapping("/list")
    @PreAuthorize("hasAuthority('system:tenant-package:list')")
    public Result<PageResult<TenantPackageResponse>> list(TenantPackageQueryRequest request) {
        Page<TenantPackageResponse> page = packageService.pagePackages(request);
        PageResult<TenantPackageResponse> result = PageResult.of(
                page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize()
        );
        return Result.success(result);
    }

    @Operation(summary = "获取套餐详情")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('system:tenant-package:list')")
    public Result<TenantPackageResponse> getInfo(@PathVariable Long id) {
        return Result.success(packageService.getPackageDetail(id));
    }

    @Operation(summary = "获取所有启用的套餐")
    @GetMapping("/enabled")
    public Result<List<TenantPackageResponse>> enabled() {
        return Result.success(packageService.getAllEnabledPackages());
    }

    @Operation(summary = "新增套餐")
    @PostMapping
    @PreAuthorize("hasAuthority('system:tenant-package:add')")
    @OperationLog(title = "租户套餐管理", businessType = OperationLog.BusinessType.INSERT)
    public Result<Void> add(@Valid @RequestBody TenantPackageRequest request) {
        TenantContextHolder.setIgnore(true);
        try {
            packageService.addPackage(request);
        } finally {
            TenantContextHolder.setIgnore(false);
        }
        return Result.success();
    }

    @Operation(summary = "更新套餐")
    @PutMapping
    @PreAuthorize("hasAuthority('system:tenant-package:update')")
    @OperationLog(title = "租户套餐管理", businessType = OperationLog.BusinessType.UPDATE)
    public Result<Void> edit(@Valid @RequestBody TenantPackageRequest request) {
        TenantContextHolder.setIgnore(true);
        try {
            packageService.updatePackage(request);
        } finally {
            TenantContextHolder.setIgnore(false);
        }
        return Result.success();
    }

    @Operation(summary = "删除套餐")
    @DeleteMapping("/{ids}")
    @PreAuthorize("hasAuthority('system:tenant-package:delete')")
    @OperationLog(title = "租户套餐管理", businessType = OperationLog.BusinessType.DELETE)
    public Result<Void> delete(@PathVariable List<Long> ids) {
        TenantContextHolder.setIgnore(true);
        try {
            packageService.deletePackages(ids);
        } finally {
            TenantContextHolder.setIgnore(false);
        }
        return Result.success();
    }
}
