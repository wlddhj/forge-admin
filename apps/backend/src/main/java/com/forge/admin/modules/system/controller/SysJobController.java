package com.forge.admin.modules.system.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.forge.admin.common.annotation.OperationLog;
import com.forge.admin.common.response.PageResult;
import com.forge.admin.common.response.Result;
import com.forge.admin.modules.system.dto.job.JobQueryRequest;
import com.forge.admin.modules.system.dto.job.JobRequest;
import com.forge.admin.modules.system.dto.job.JobResponse;
import com.forge.admin.modules.system.service.SysJobService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * 定时任务控制器
 */
@Tag(name = "定时任务管理")
@RestController
@RequestMapping("/system/job")
@RequiredArgsConstructor
public class SysJobController {

    private final SysJobService sysJobService;

    @Operation(summary = "分页查询定时任务")
    @GetMapping("/list")
    @PreAuthorize("hasAuthority('system:job:list')")
    public Result<PageResult<JobResponse>> list(JobQueryRequest request) {
        Page<JobResponse> page = sysJobService.pageJobs(request);
        PageResult<JobResponse> result = PageResult.of(
                page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize()
        );
        return Result.success(result);
    }

    @Operation(summary = "获取任务详情")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('system:job:query')")
    public Result<JobResponse> getInfo(@PathVariable Long id) {
        return Result.success(sysJobService.getJobDetail(id));
    }

    @Operation(summary = "新增任务")
    @PostMapping
    @PreAuthorize("hasAuthority('system:job:add')")
    @OperationLog(title = "定时任务管理", businessType = OperationLog.BusinessType.INSERT)
    public Result<Void> add(@Valid @RequestBody JobRequest request) {
        sysJobService.addJob(request);
        return Result.success();
    }

    @Operation(summary = "更新任务")
    @PutMapping
    @PreAuthorize("hasAuthority('system:job:edit')")
    @OperationLog(title = "定时任务管理", businessType = OperationLog.BusinessType.UPDATE)
    public Result<Void> edit(@Valid @RequestBody JobRequest request) {
        sysJobService.updateJob(request);
        return Result.success();
    }

    @Operation(summary = "删除任务")
    @DeleteMapping("/{ids}")
    @PreAuthorize("hasAuthority('system:job:delete')")
    @OperationLog(title = "定时任务管理", businessType = OperationLog.BusinessType.DELETE)
    public Result<Void> delete(@PathVariable Long[] ids) {
        sysJobService.deleteJobs(ids);
        return Result.success();
    }

    @Operation(summary = "修改任务状态")
    @PutMapping("/{id}/status")
    @PreAuthorize("hasAuthority('system:job:edit')")
    @OperationLog(title = "定时任务管理", businessType = OperationLog.BusinessType.UPDATE)
    public Result<Void> changeStatus(@PathVariable Long id, @RequestParam Integer status) {
        sysJobService.changeStatus(id, status);
        return Result.success();
    }

    @Operation(summary = "立即执行一次")
    @PostMapping("/{id}/run")
    @PreAuthorize("hasAuthority('system:job:edit')")
    @OperationLog(title = "定时任务管理", businessType = OperationLog.BusinessType.OTHER)
    public Result<Void> runOnce(@PathVariable Long id) {
        sysJobService.runOnce(id);
        return Result.success();
    }
}
