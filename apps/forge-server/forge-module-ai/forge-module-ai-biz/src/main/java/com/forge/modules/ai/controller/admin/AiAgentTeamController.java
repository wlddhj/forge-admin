package com.forge.modules.ai.controller.admin;

import com.forge.common.response.Result;
import com.forge.framework.web.annotation.OperationLog;
import com.forge.modules.ai.dto.team.TeamResponse;
import com.forge.modules.ai.dto.team.TeamSaveRequest;
import com.forge.modules.ai.service.AiAgentTeamService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * AI Agent 团队控制器：Supervisor 调度式多 Agent 协作配置
 */
@Tag(name = "AI Agent团队")
@RestController
@RequestMapping("/ai/team")
@RequiredArgsConstructor
public class AiAgentTeamController {

    private final AiAgentTeamService teamService;

    @Operation(summary = "团队列表（含成员聚合）")
    @GetMapping("/list")
    @PreAuthorize("hasAuthority('ai:team:query')")
    public Result<List<TeamResponse>> list(@RequestParam(required = false) Integer status) {
        return Result.success(teamService.list(status));
    }

    @Operation(summary = "团队详情")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('ai:team:query')")
    public Result<TeamResponse> get(@PathVariable Long id) {
        return Result.success(teamService.get(id));
    }

    @Operation(summary = "新增团队")
    @PostMapping
    @PreAuthorize("hasAuthority('ai:team:add')")
    @OperationLog(title = "AI Agent团队", businessType = OperationLog.BusinessType.INSERT)
    public Result<Void> add(@Valid @RequestBody TeamSaveRequest request) {
        teamService.add(request);
        return Result.success();
    }

    @Operation(summary = "更新团队")
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ai:team:edit')")
    @OperationLog(title = "AI Agent团队", businessType = OperationLog.BusinessType.UPDATE)
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody TeamSaveRequest request) {
        teamService.update(id, request);
        return Result.success();
    }

    @Operation(summary = "删除团队")
    @DeleteMapping("/{ids}")
    @PreAuthorize("hasAuthority('ai:team:remove')")
    @OperationLog(title = "AI Agent团队", businessType = OperationLog.BusinessType.DELETE)
    public Result<Void> delete(@PathVariable List<Long> ids) {
        teamService.delete(ids);
        return Result.success();
    }
}
