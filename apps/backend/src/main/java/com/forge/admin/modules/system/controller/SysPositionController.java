package com.forge.admin.modules.system.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.forge.admin.common.annotation.OperationLog;
import com.forge.admin.common.response.PageResult;
import com.forge.admin.common.response.Result;
import com.forge.admin.modules.system.dto.position.PositionQueryRequest;
import com.forge.admin.modules.system.dto.position.PositionRequest;
import com.forge.admin.modules.system.dto.position.PositionResponse;
import com.forge.admin.modules.system.service.SysPositionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 岗位控制器
 */
@Tag(name = "岗位管理")
@RestController
@RequestMapping("/system/position")
@RequiredArgsConstructor
public class SysPositionController {

    private final SysPositionService sysPositionService;

    @Operation(summary = "分页查询岗位")
    @GetMapping("/list")
    @PreAuthorize("hasAuthority('system:position:list')")
    public Result<PageResult<PositionResponse>> list(PositionQueryRequest request) {
        Page<PositionResponse> page = sysPositionService.pagePositions(request);
        PageResult<PositionResponse> result = PageResult.of(
                page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize()
        );
        return Result.success(result);
    }

    @Operation(summary = "获取所有岗位")
    @GetMapping("/all")
    public Result<List<PositionResponse>> all() {
        return Result.success(sysPositionService.getAllPositions());
    }

    @Operation(summary = "获取岗位详情")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('system:position:query')")
    public Result<PositionResponse> getInfo(@PathVariable Long id) {
        return Result.success(sysPositionService.getPositionDetail(id));
    }

    @Operation(summary = "新增岗位")
    @PostMapping
    @PreAuthorize("hasAuthority('system:position:add')")
    @OperationLog(title = "岗位管理", businessType = OperationLog.BusinessType.INSERT)
    public Result<Void> add(@Valid @RequestBody PositionRequest request) {
        sysPositionService.addPosition(request);
        return Result.success();
    }

    @Operation(summary = "更新岗位")
    @PutMapping
    @PreAuthorize("hasAuthority('system:position:edit')")
    @OperationLog(title = "岗位管理", businessType = OperationLog.BusinessType.UPDATE)
    public Result<Void> edit(@Valid @RequestBody PositionRequest request) {
        sysPositionService.updatePosition(request);
        return Result.success();
    }

    @Operation(summary = "删除岗位")
    @DeleteMapping("/{ids}")
    @PreAuthorize("hasAuthority('system:position:delete')")
    @OperationLog(title = "岗位管理", businessType = OperationLog.BusinessType.DELETE)
    public Result<Void> delete(@PathVariable List<Long> ids) {
        sysPositionService.deletePositions(ids);
        return Result.success();
    }

    @Operation(summary = "更新岗位状态")
    @PutMapping("/{id}/status")
    @PreAuthorize("hasAuthority('system:position:edit')")
    @OperationLog(title = "岗位管理", businessType = OperationLog.BusinessType.UPDATE)
    public Result<Void> updateStatus(@PathVariable Long id, @RequestParam Integer status) {
        sysPositionService.updateStatus(id, status);
        return Result.success();
    }
}
