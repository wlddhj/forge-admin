package com.forge.admin.modules.system.controller;

import com.forge.admin.common.annotation.OperationLog;
import com.forge.admin.common.response.Result;
import com.forge.admin.modules.system.dto.menu.MenuRequest;
import com.forge.admin.modules.system.dto.menu.MenuResponse;
import com.forge.admin.modules.system.dto.menu.MenuTreeResponse;
import com.forge.admin.modules.system.service.SysMenuService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 菜单控制器
 *
 * @author standadmin
 */
@Tag(name = "菜单管理")
@RestController
@RequestMapping("/system/menu")
@RequiredArgsConstructor
public class SysMenuController {

    private final SysMenuService sysMenuService;

    @Operation(summary = "查询菜单列表")
    @GetMapping("/list")
    @PreAuthorize("hasAuthority('system:menu:list')")
    public Result<List<MenuResponse>> list(
            @RequestParam(required = false) String menuName,
            @RequestParam(required = false) Integer status) {
        return Result.success(sysMenuService.listMenus(menuName, status));
    }

    @Operation(summary = "获取菜单树")
    @GetMapping("/tree")
    public Result<List<MenuTreeResponse>> tree() {
        return Result.success(sysMenuService.getMenuTree());
    }

    @Operation(summary = "获取菜单详情")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('system:menu:query')")
    public Result<MenuResponse> getInfo(@PathVariable Long id) {
        return Result.success(sysMenuService.getMenuDetail(id));
    }

    @Operation(summary = "新增菜单")
    @PostMapping
    @PreAuthorize("hasAuthority('system:menu:add')")
    @OperationLog(title = "菜单管理", businessType = OperationLog.BusinessType.INSERT)
    public Result<Void> add(@Valid @RequestBody MenuRequest request) {
        sysMenuService.addMenu(request);
        return Result.success();
    }

    @Operation(summary = "更新菜单")
    @PutMapping
    @PreAuthorize("hasAuthority('system:menu:edit')")
    @OperationLog(title = "菜单管理", businessType = OperationLog.BusinessType.UPDATE)
    public Result<Void> edit(@Valid @RequestBody MenuRequest request) {
        sysMenuService.updateMenu(request);
        return Result.success();
    }

    @Operation(summary = "删除菜单")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('system:menu:delete')")
    @OperationLog(title = "菜单管理", businessType = OperationLog.BusinessType.DELETE)
    public Result<Void> delete(@PathVariable Long id) {
        sysMenuService.deleteMenu(id);
        return Result.success();
    }
}
