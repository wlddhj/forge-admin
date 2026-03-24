package com.forge.admin.modules.system.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.forge.admin.common.annotation.OperationLog;
import com.forge.admin.common.response.PageResult;
import com.forge.admin.common.response.Result;
import com.forge.admin.modules.system.dto.role.RoleQueryRequest;
import com.forge.admin.modules.system.dto.role.RoleRequest;
import com.forge.admin.modules.system.dto.role.RoleResponse;
import com.forge.admin.modules.system.service.SysRoleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 角色控制器
 *
 * @author standadmin
 */
@Tag(name = "角色管理")
@RestController
@RequestMapping("/system/role")
@RequiredArgsConstructor
public class SysRoleController {

    private final SysRoleService sysRoleService;

    @Operation(summary = "分页查询角色")
    @GetMapping("/list")
    @PreAuthorize("hasAuthority('system:role:list')")
    public Result<PageResult<RoleResponse>> list(RoleQueryRequest request) {
        Page<RoleResponse> page = sysRoleService.pageRoles(request);
        PageResult<RoleResponse> result = PageResult.of(
                page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize()
        );
        return Result.success(result);
    }

    @Operation(summary = "获取所有角色")
    @GetMapping("/all")
    public Result<List<RoleResponse>> all() {
        return Result.success(sysRoleService.getAllRoles());
    }

    @Operation(summary = "获取角色详情")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('system:role:query')")
    public Result<RoleResponse> getInfo(@PathVariable Long id) {
        return Result.success(sysRoleService.getRoleDetail(id));
    }

    @Operation(summary = "新增角色")
    @PostMapping
    @PreAuthorize("hasAuthority('system:role:add')")
    @OperationLog(title = "角色管理", businessType = OperationLog.BusinessType.INSERT)
    public Result<Void> add(@Valid @RequestBody RoleRequest request) {
        sysRoleService.addRole(request);
        return Result.success();
    }

    @Operation(summary = "更新角色")
    @PutMapping
    @PreAuthorize("hasAuthority('system:role:edit')")
    @OperationLog(title = "角色管理", businessType = OperationLog.BusinessType.UPDATE)
    public Result<Void> edit(@Valid @RequestBody RoleRequest request) {
        sysRoleService.updateRole(request);
        return Result.success();
    }

    @Operation(summary = "删除角色")
    @DeleteMapping("/{ids}")
    @PreAuthorize("hasAuthority('system:role:delete')")
    @OperationLog(title = "角色管理", businessType = OperationLog.BusinessType.DELETE)
    public Result<Void> delete(@PathVariable List<Long> ids) {
        sysRoleService.deleteRoles(ids);
        return Result.success();
    }

    @Operation(summary = "更新角色状态")
    @PutMapping("/{id}/status")
    @PreAuthorize("hasAuthority('system:role:edit')")
    @OperationLog(title = "角色管理", businessType = OperationLog.BusinessType.UPDATE)
    public Result<Void> updateStatus(@PathVariable Long id, @RequestParam Integer status) {
        sysRoleService.updateStatus(id, status);
        return Result.success();
    }

    @Operation(summary = "获取角色菜单ID列表")
    @GetMapping("/{id}/menus")
    @PreAuthorize("hasAuthority('system:role:query')")
    public Result<List<Long>> getRoleMenus(@PathVariable Long id) {
        return Result.success(sysRoleService.getRoleMenuIds(id));
    }

    @Operation(summary = "分配菜单权限")
    @PutMapping("/{id}/menus")
    @PreAuthorize("hasAuthority('system:role:edit')")
    @OperationLog(title = "角色管理", businessType = OperationLog.BusinessType.GRANT)
    public Result<Void> assignMenus(@PathVariable Long id, @RequestBody List<Long> menuIds) {
        sysRoleService.assignMenus(id, menuIds);
        return Result.success();
    }
}
