package com.forge.modules.ai.controller.admin;

import com.forge.common.response.Result;
import com.forge.framework.web.annotation.OperationLog;
import com.forge.modules.ai.agent.job.AiAgentJobService;
import com.forge.modules.ai.entity.AiAgentJob;
import com.forge.modules.ai.entity.AiAgentRun;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * AI Agent 定时任务控制器
 */
@Tag(name = "AI Agent定时任务")
@RestController
@RequestMapping("/ai/agent/job")
@RequiredArgsConstructor
public class AiAgentJobController {

    private final AiAgentJobService agentJobService;

    @Operation(summary = "任务分页列表")
    @GetMapping("/list")
    @PreAuthorize("hasAuthority('ai:agent:query')")
    public Result<Map<String, Object>> list(@RequestParam(required = false) String jobName,
                                            @RequestParam(required = false) String agentCode,
                                            @RequestParam(required = false) Integer status,
                                            @RequestParam(defaultValue = "1") int pageNum,
                                            @RequestParam(defaultValue = "10") int pageSize) {
        return Result.success(agentJobService.pageJobs(jobName, agentCode, status, pageNum, pageSize));
    }

    @Operation(summary = "新增任务")
    @PostMapping
    @PreAuthorize("hasAuthority('ai:agent:job:config')")
    @OperationLog(title = "AI Agent任务", businessType = OperationLog.BusinessType.INSERT)
    public Result<Void> add(@RequestBody AiAgentJob job) {
        agentJobService.addJob(job);
        return Result.success();
    }

    @Operation(summary = "更新任务")
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ai:agent:job:config')")
    @OperationLog(title = "AI Agent任务", businessType = OperationLog.BusinessType.UPDATE)
    public Result<Void> update(@PathVariable Long id, @RequestBody AiAgentJob job) {
        agentJobService.updateJob(id, job);
        return Result.success();
    }

    @Operation(summary = "启停任务")
    @PutMapping("/{id}/status")
    @PreAuthorize("hasAuthority('ai:agent:job:config')")
    @OperationLog(title = "AI Agent任务", businessType = OperationLog.BusinessType.UPDATE)
    public Result<Void> changeStatus(@PathVariable Long id, @RequestParam Integer status) {
        agentJobService.changeStatus(id, status);
        return Result.success();
    }

    @Operation(summary = "删除任务")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ai:agent:job:config')")
    @OperationLog(title = "AI Agent任务", businessType = OperationLog.BusinessType.DELETE)
    public Result<Void> delete(@PathVariable Long id) {
        agentJobService.deleteJob(id);
        return Result.success();
    }

    @Operation(summary = "立即执行一次")
    @PostMapping("/{id}/run")
    @PreAuthorize("hasAuthority('ai:agent:job:run')")
    @OperationLog(title = "AI Agent任务执行", businessType = OperationLog.BusinessType.OTHER)
    public Result<AiAgentRun> run(@PathVariable Long id) {
        return Result.success(agentJobService.runOnce(id));
    }
}
