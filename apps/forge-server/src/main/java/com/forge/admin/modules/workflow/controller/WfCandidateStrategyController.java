package com.forge.admin.modules.workflow.controller;

import com.forge.admin.common.response.Result;
import com.forge.admin.modules.workflow.dto.candidate.CandidateStrategyResponse;
import com.forge.admin.modules.workflow.framework.candidate.BpmTaskCandidateInvoker;
import com.forge.admin.modules.workflow.framework.candidate.BpmTaskCandidateStrategy;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 候选人策略控制器
 *
 * @author forge-admin
 */
@Tag(name = "候选人策略")
@RestController
@RequestMapping("/workflow/candidate-strategy")
@RequiredArgsConstructor
public class WfCandidateStrategyController {

    private final BpmTaskCandidateInvoker candidateInvoker;

    @Operation(summary = "获取候选人策略列表")
    @GetMapping("/list")
    public Result<List<CandidateStrategyResponse>> list() {
        List<BpmTaskCandidateStrategy> strategies = candidateInvoker.getStrategyList();
        List<CandidateStrategyResponse> responses = strategies.stream()
                .map(s -> new CandidateStrategyResponse(s.getStrategy(), s.getDescription()))
                .sorted((a, b) -> a.getCode().compareTo(b.getCode()))
                .toList();
        return Result.success(responses);
    }
}