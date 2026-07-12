package com.forge.modules.system.controller.admin;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.forge.common.response.PageResult;
import com.forge.common.response.Result;
import com.forge.framework.tenant.core.context.TenantContextHolder;
import com.forge.framework.web.annotation.OperationLog;
import com.forge.modules.system.dto.tenant.TenantQueryRequest;
import com.forge.modules.system.dto.tenant.TenantRequest;
import com.forge.modules.system.dto.tenant.TenantResponse;
import com.forge.modules.system.service.SysTenantService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 租户管理控制器
 *
 * @author standadmin
 */
@Tag(name = "租户管理")
@RestController
@RequestMapping("/system/tenant")
@RequiredArgsConstructor
public class SysTenantController {

    private final SysTenantService tenantService;

    @Operation(summary = "分页查询租户")
    @GetMapping("/list")
    @PreAuthorize("hasAuthority('system:tenant:list')")
    public Result<PageResult<TenantResponse>> list(TenantQueryRequest request) {
        Page<TenantResponse> page = tenantService.pageTenants(request);
        PageResult<TenantResponse> result = PageResult.of(
                page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize()
        );
        return Result.success(result);
    }

    @Operation(summary = "获取租户详情")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('system:tenant:list')")
    public Result<TenantResponse> getInfo(@PathVariable Long id) {
        return Result.success(tenantService.getTenantDetail(id));
    }

    @Operation(summary = "新增租户")
    @PostMapping
    @PreAuthorize("hasAuthority('system:tenant:add')")
    @OperationLog(title = "租户管理", businessType = OperationLog.BusinessType.INSERT)
    public Result<Void> add(@Valid @RequestBody TenantRequest request) {
        TenantContextHolder.setIgnore(true);
        try {
            tenantService.addTenant(request);
        } finally {
            TenantContextHolder.setIgnore(false);
        }
        return Result.success();
    }

    @Operation(summary = "更新租户")
    @PutMapping
    @PreAuthorize("hasAuthority('system:tenant:update')")
    @OperationLog(title = "租户管理", businessType = OperationLog.BusinessType.UPDATE)
    public Result<Void> edit(@Valid @RequestBody TenantRequest request) {
        TenantContextHolder.setIgnore(true);
        try {
            tenantService.updateTenant(request);
        } finally {
            TenantContextHolder.setIgnore(false);
        }
        return Result.success();
    }

    @Operation(summary = "删除租户")
    @DeleteMapping("/{ids}")
    @PreAuthorize("hasAuthority('system:tenant:delete')")
    @OperationLog(title = "租户管理", businessType = OperationLog.BusinessType.DELETE)
    public Result<Void> delete(@PathVariable List<Long> ids) {
        TenantContextHolder.setIgnore(true);
        try {
            tenantService.deleteTenants(ids);
        } finally {
            TenantContextHolder.setIgnore(false);
        }
        return Result.success();
    }

    @Operation(summary = "更新租户状态")
    @PutMapping("/{id}/status")
    @PreAuthorize("hasAuthority('system:tenant:update')")
    @OperationLog(title = "租户管理", businessType = OperationLog.BusinessType.UPDATE)
    public Result<Void> changeStatus(@PathVariable Long id, @RequestParam Integer status) {
        TenantContextHolder.setIgnore(true);
        try {
            tenantService.changeStatus(id, status);
        } finally {
            TenantContextHolder.setIgnore(false);
        }
        return Result.success();
    }
}
