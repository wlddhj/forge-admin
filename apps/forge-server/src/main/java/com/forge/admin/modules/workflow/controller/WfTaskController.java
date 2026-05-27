package com.forge.admin.modules.workflow.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.forge.admin.common.annotation.OperationLog;
import com.forge.admin.common.response.PageResult;
import com.forge.admin.common.response.Result;
import com.forge.admin.modules.workflow.dto.task.*;
import com.forge.admin.modules.workflow.service.WfTaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 任务管理控制器
 *
 * @author forge-admin
 */
@Tag(name = "任务管理")
@RestController
@RequestMapping("/workflow/task")
@RequiredArgsConstructor
public class WfTaskController {

    private final WfTaskService wfTaskService;

    @Operation(summary = "查询待办任务")
    @GetMapping("/todo")
    @PreAuthorize("hasAuthority('workflow:task:list')")
    public Result<PageResult<TaskResponse>> todoTasks(TaskQueryRequest request) {
        Page<TaskResponse> page = wfTaskService.getTodoTasks(request);
        PageResult<TaskResponse> result = PageResult.of(
                page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize()
        );
        return Result.success(result);
    }

    @Operation(summary = "查询可签收任务")
    @GetMapping("/claimable")
    @PreAuthorize("hasAuthority('workflow:task:list')")
    public Result<PageResult<TaskResponse>> claimableTasks(TaskQueryRequest request) {
        Page<TaskResponse> page = wfTaskService.getClaimableTasks(request);
        PageResult<TaskResponse> result = PageResult.of(
                page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize()
        );
        return Result.success(result);
    }

    @Operation(summary = "查询已办任务")
    @GetMapping("/done")
    @PreAuthorize("hasAuthority('workflow:task:list')")
    public Result<PageResult<TaskResponse>> doneTasks(TaskQueryRequest request) {
        Page<TaskResponse> page = wfTaskService.getDoneTasks(request);
        PageResult<TaskResponse> result = PageResult.of(
                page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize()
        );
        return Result.success(result);
    }

    @Operation(summary = "获取任务详情")
    @GetMapping("/{taskId}")
    @PreAuthorize("hasAuthority('workflow:task:query')")
    public Result<TaskResponse> getInfo(@PathVariable String taskId) {
        return Result.success(wfTaskService.getTaskById(taskId));
    }

    @Operation(summary = "签收任务")
    @PostMapping("/{taskId}/claim")
    @PreAuthorize("hasAuthority('workflow:task:claim')")
    @OperationLog(title = "任务管理", businessType = OperationLog.BusinessType.UPDATE)
    public Result<Void> claim(@PathVariable String taskId) {
        wfTaskService.claimTask(taskId);
        return Result.success();
    }

    @Operation(summary = "取消签收任务")
    @PostMapping("/{taskId}/unclaim")
    @PreAuthorize("hasAuthority('workflow:task:claim')")
    @OperationLog(title = "任务管理", businessType = OperationLog.BusinessType.UPDATE)
    public Result<Void> unclaim(@PathVariable String taskId) {
        wfTaskService.unclaimTask(taskId);
        return Result.success();
    }

    @Operation(summary = "完成任务")
    @PostMapping("/{taskId}/complete")
    @PreAuthorize("hasAuthority('workflow:task:complete')")
    @OperationLog(title = "任务管理", businessType = OperationLog.BusinessType.UPDATE)
    public Result<Void> complete(@PathVariable String taskId, @RequestBody TaskCompleteRequest request) {
        wfTaskService.completeTask(taskId, request);
        return Result.success();
    }

    @Operation(summary = "审批通过")
    @PostMapping("/{taskId}/approve")
    @PreAuthorize("hasAuthority('workflow:task:complete')")
    @OperationLog(title = "任务管理", businessType = OperationLog.BusinessType.UPDATE)
    public Result<Void> approve(@PathVariable String taskId, @RequestBody TaskCompleteRequest request) {
        wfTaskService.approveTask(taskId, request);
        return Result.success();
    }

    @Operation(summary = "审批驳回")
    @PostMapping("/{taskId}/reject")
    @PreAuthorize("hasAuthority('workflow:task:complete')")
    @OperationLog(title = "任务管理", businessType = OperationLog.BusinessType.UPDATE)
    public Result<Void> reject(@PathVariable String taskId, @RequestBody TaskCompleteRequest request) {
        wfTaskService.rejectTask(taskId, request);
        return Result.success();
    }

    @Operation(summary = "委派任务")
    @PostMapping("/{taskId}/delegate")
    @PreAuthorize("hasAuthority('workflow:task:delegate')")
    @OperationLog(title = "任务管理", businessType = OperationLog.BusinessType.UPDATE)
    public Result<Void> delegate(@PathVariable String taskId, @Valid @RequestBody TaskDelegateRequest request) {
        wfTaskService.delegateTask(taskId, request);
        return Result.success();
    }

    @Operation(summary = "转办任务")
    @PostMapping("/{taskId}/transfer")
    @PreAuthorize("hasAuthority('workflow:task:transfer')")
    @OperationLog(title = "任务管理", businessType = OperationLog.BusinessType.UPDATE)
    public Result<Void> transfer(@PathVariable String taskId, @Valid @RequestBody TaskTransferRequest request) {
        wfTaskService.transferTask(taskId, request);
        return Result.success();
    }

    @Operation(summary = "退回任务")
    @PostMapping("/{taskId}/return")
    @PreAuthorize("hasAuthority('workflow:task:return')")
    @OperationLog(title = "任务管理", businessType = OperationLog.BusinessType.UPDATE)
    public Result<Void> returnTask(@PathVariable String taskId, @Valid @RequestBody TaskReturnRequest request) {
        wfTaskService.returnTask(taskId, request);
        return Result.success();
    }

    @Operation(summary = "获取可退回节点")
    @GetMapping("/{taskId}/return-nodes")
    @PreAuthorize("hasAuthority('workflow:task:query')")
    public Result<List<Map<String, String>>> getReturnNodes(@PathVariable String taskId) {
        return Result.success(wfTaskService.getReturnNodes(taskId));
    }

    @Operation(summary = "加签")
    @PostMapping("/{taskId}/sign-create")
    @PreAuthorize("hasAuthority('workflow:task:complete')")
    @OperationLog(title = "任务管理", businessType = OperationLog.BusinessType.UPDATE)
    public Result<Void> signCreate(@PathVariable String taskId, @Valid @RequestBody TaskSignCreateRequest request) {
        request.setTaskId(taskId);
        wfTaskService.signCreateTask(taskId, request);
        return Result.success();
    }

    @Operation(summary = "减签")
    @PostMapping("/{taskId}/sign-delete")
    @PreAuthorize("hasAuthority('workflow:task:complete')")
    @OperationLog(title = "任务管理", businessType = OperationLog.BusinessType.UPDATE)
    public Result<Void> signDelete(@PathVariable String taskId, @Valid @RequestBody TaskSignDeleteRequest request) {
        request.setTaskId(taskId);
        wfTaskService.signDeleteTask(taskId, request);
        return Result.success();
    }

    @Operation(summary = "抄送")
    @PostMapping("/{taskId}/copy")
    @PreAuthorize("hasAuthority('workflow:task:complete')")
    @OperationLog(title = "任务管理", businessType = OperationLog.BusinessType.UPDATE)
    public Result<Void> copy(@PathVariable String taskId, @Valid @RequestBody TaskCopyRequest request) {
        request.setTaskId(taskId);
        wfTaskService.copyTask(taskId, request);
        return Result.success();
    }

    @Operation(summary = "撤回")
    @PostMapping("/{taskId}/withdraw")
    @PreAuthorize("hasAuthority('workflow:task:complete')")
    @OperationLog(title = "任务管理", businessType = OperationLog.BusinessType.UPDATE)
    public Result<Void> withdraw(@PathVariable String taskId) {
        wfTaskService.withdrawTask(taskId);
        return Result.success();
    }

    @Operation(summary = "获取子任务列表")
    @GetMapping("/{taskId}/child-tasks")
    @PreAuthorize("hasAuthority('workflow:task:query')")
    public Result<List<Map<String, String>>> getChildTasks(@PathVariable String taskId) {
        return Result.success(wfTaskService.getChildTasks(taskId));
    }
}
