package com.forge.admin.modules.system.controller;

import com.forge.admin.common.annotation.OperationLog;
import com.forge.admin.common.response.Result;
import com.forge.admin.modules.system.dto.dept.DeptRequest;
import com.forge.admin.modules.system.dto.dept.DeptResponse;
import com.forge.admin.modules.system.dto.dept.DeptTreeResponse;
import com.forge.admin.modules.system.service.SysDeptService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 部门控制器
 *
 * @author standadmin
 */
@Tag(name = "部门管理")
@RestController
@RequestMapping("/system/dept")
@RequiredArgsConstructor
public class SysDeptController {

    private final SysDeptService sysDeptService;

    @Operation(summary = "查询部门列表")
    @GetMapping("/list")
    @PreAuthorize("hasAuthority('system:dept:list')")
    public Result<List<DeptResponse>> list(
            @RequestParam(required = false) String deptName,
            @RequestParam(required = false) Integer status) {
        return Result.success(sysDeptService.listDepts(deptName, status));
    }

    @Operation(summary = "获取部门树")
    @GetMapping("/tree")
    public Result<List<DeptTreeResponse>> tree() {
        return Result.success(sysDeptService.getDeptTree());
    }

    @Operation(summary = "获取部门详情")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('system:dept:query')")
    public Result<DeptResponse> getInfo(@PathVariable Long id) {
        return Result.success(sysDeptService.getDeptDetail(id));
    }

    @Operation(summary = "新增部门")
    @PostMapping
    @PreAuthorize("hasAuthority('system:dept:add')")
    @OperationLog(title = "部门管理", businessType = OperationLog.BusinessType.INSERT)
    public Result<Void> add(@Valid @RequestBody DeptRequest request) {
        sysDeptService.addDept(request);
        return Result.success();
    }

    @Operation(summary = "更新部门")
    @PutMapping
    @PreAuthorize("hasAuthority('system:dept:edit')")
    @OperationLog(title = "部门管理", businessType = OperationLog.BusinessType.UPDATE)
    public Result<Void> edit(@Valid @RequestBody DeptRequest request) {
        sysDeptService.updateDept(request);
        return Result.success();
    }

    @Operation(summary = "删除部门")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('system:dept:delete')")
    @OperationLog(title = "部门管理", businessType = OperationLog.BusinessType.DELETE)
    public Result<Void> delete(@PathVariable Long id) {
        sysDeptService.deleteDept(id);
        return Result.success();
    }
}
