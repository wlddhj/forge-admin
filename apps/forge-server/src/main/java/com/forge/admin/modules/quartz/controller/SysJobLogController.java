package com.forge.admin.modules.quartz.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.forge.admin.common.annotation.OperationLog;
import com.forge.admin.common.response.PageResult;
import com.forge.admin.common.response.Result;
import com.forge.admin.modules.quartz.entity.SysJobLog;
import com.forge.admin.modules.quartz.service.JobLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * 任务执行日志控制器
 */
@Tag(name = "任务执行日志管理")
@RestController
@RequestMapping("/system/job-log")
@RequiredArgsConstructor
public class SysJobLogController {

    private final JobLogService jobLogService;

    @Operation(summary = "分页查询任务日志")
    @GetMapping("/list")
    @PreAuthorize("hasAuthority('system:job:query')")
    public Result<PageResult<SysJobLog>> list(
            @RequestParam(required = false) Long jobId,
            @RequestParam(required = false) String jobName,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {

        Page<SysJobLog> page = jobLogService.pageJobLogs(jobId, jobName, status, pageNum, pageSize);
        PageResult<SysJobLog> result = PageResult.of(
                page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize()
        );
        return Result.success(result);
    }

    @Operation(summary = "获取日志详情")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('system:job:query')")
    public Result<SysJobLog> getInfo(@PathVariable Long id) {
        return Result.success(jobLogService.getById(id));
    }

    @Operation(summary = "删除日志")
    @DeleteMapping("/{ids}")
    @PreAuthorize("hasAuthority('system:job:delete')")
    @OperationLog(title = "任务执行日志", businessType = OperationLog.BusinessType.DELETE)
    public Result<Void> delete(@PathVariable Long[] ids) {
        jobLogService.removeByIds(java.util.Arrays.asList(ids));
        return Result.success();
    }

    @Operation(summary = "清空任务日志")
    @DeleteMapping("/clear")
    @PreAuthorize("hasAuthority('system:job:delete')")
    @OperationLog(title = "任务执行日志", businessType = OperationLog.BusinessType.CLEAN)
    public Result<Void> clear(@RequestParam(required = false) Long jobId) {
        jobLogService.clearJobLogs(jobId);
        return Result.success();
    }
}
