package com.forge.modules.ai.controller.admin;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.forge.common.response.Result;
import com.forge.framework.web.annotation.OperationLog;
import com.forge.modules.ai.entity.AiApiUsage;
import com.forge.modules.ai.service.AiUsageQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * AI 用量统计控制器(查询侧,记账见 AiUsageRecorder):分页明细 + 汇总
 */
@Tag(name = "AI用量统计")
@RestController
@RequestMapping("/ai/usage")
@RequiredArgsConstructor
public class AiApiUsageController {

    private final AiUsageQueryService usageQueryService;

    @Operation(summary = "用量明细分页(按日聚合行)")
    @GetMapping("/page")
    @PreAuthorize("hasAuthority('ai:usage:query')")
    public Result<Page<AiApiUsage>> page(@RequestParam(required = false) Long userId,
                                         @RequestParam(required = false) Long modelId,
                                         @RequestParam(required = false) String startDate,
                                         @RequestParam(required = false) String endDate,
                                         @RequestParam(defaultValue = "1") int pageNum,
                                         @RequestParam(defaultValue = "10") int pageSize) {
        return Result.success(usageQueryService.page(userId, modelId, startDate, endDate, pageNum, pageSize));
    }

    @Operation(summary = "用量汇总(总请求/成功率/Token/均时延)")
    @GetMapping("/summary")
    @PreAuthorize("hasAuthority('ai:usage:query')")
    public Result<Map<String, Object>> summary(@RequestParam(required = false) Long userId,
                                               @RequestParam(required = false) Long modelId,
                                               @RequestParam(required = false) String startDate,
                                               @RequestParam(required = false) String endDate) {
        return Result.success(usageQueryService.summary(userId, modelId, startDate, endDate));
    }
}
