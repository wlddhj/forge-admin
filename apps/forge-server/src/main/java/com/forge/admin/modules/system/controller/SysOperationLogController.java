package com.forge.admin.modules.system.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.forge.admin.common.response.PageResult;
import com.forge.admin.common.response.Result;
import com.forge.admin.modules.system.dto.log.OperationLogQueryRequest;
import com.forge.admin.modules.system.dto.log.OperationLogResponse;
import com.forge.admin.modules.system.service.SysOperationLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Tag(name = "操作日志管理")
@RestController
@RequestMapping("/system/operation-log")
@RequiredArgsConstructor
public class SysOperationLogController {

    private final SysOperationLogService sysOperationLogService;

    @Operation(summary = "分页查询操作日志")
    @GetMapping("/list")
    @PreAuthorize("hasAuthority('system:log:list')")
    public Result<PageResult<OperationLogResponse>> list(OperationLogQueryRequest request) {
        Page<OperationLogResponse> page = sysOperationLogService.pageLogs(request);
        PageResult<OperationLogResponse> result = PageResult.of(
                page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize()
        );
        return Result.success(result);
    }

    @Operation(summary = "获取操作日志详情")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('system:log:query')")
    public Result<OperationLogResponse> getInfo(@PathVariable Long id) {
        return Result.success(sysOperationLogService.getLogDetail(id));
    }

    @Operation(summary = "清空操作日志")
    @DeleteMapping("/clear")
    @PreAuthorize("hasAuthority('system:log:delete')")
    public Result<Void> clear() {
        sysOperationLogService.clearLogs();
        return Result.success();
    }
}
