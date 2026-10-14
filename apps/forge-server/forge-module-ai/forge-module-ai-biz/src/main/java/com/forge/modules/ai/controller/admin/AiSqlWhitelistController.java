package com.forge.modules.ai.controller.admin;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.forge.common.response.PageResult;
import com.forge.common.response.Result;
import com.forge.framework.web.annotation.OperationLog;
import com.forge.modules.ai.dto.request.SqlWhitelistQueryRequest;
import com.forge.modules.ai.dto.request.SqlWhitelistRequest;
import com.forge.modules.ai.dto.response.SqlWhitelistResponse;
import com.forge.modules.ai.service.AiSqlWhitelistService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * AI Agent SQL 工具表白名单控制器
 */
@Tag(name = "AI SQL白名单")
@RestController
@RequestMapping("/ai/sql-whitelist")
@RequiredArgsConstructor
public class AiSqlWhitelistController {

    private final AiSqlWhitelistService whitelistService;

    @Operation(summary = "分页查询")
    @GetMapping("/list")
    @PreAuthorize("hasAuthority('ai:sql-whitelist:list')")
    public Result<PageResult<SqlWhitelistResponse>> list(SqlWhitelistQueryRequest request) {
        Page<SqlWhitelistResponse> page = whitelistService.page(request);
        return Result.success(PageResult.of(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize()));
    }

    @Operation(summary = "新增")
    @PostMapping
    @PreAuthorize("hasAuthority('ai:sql-whitelist:add')")
    @OperationLog(title = "AI SQL白名单", businessType = OperationLog.BusinessType.INSERT)
    public Result<Void> add(@Valid @RequestBody SqlWhitelistRequest request) {
        whitelistService.add(request);
        return Result.success();
    }

    @Operation(summary = "修改（含启停）")
    @PutMapping
    @PreAuthorize("hasAuthority('ai:sql-whitelist:edit')")
    @OperationLog(title = "AI SQL白名单", businessType = OperationLog.BusinessType.UPDATE)
    public Result<Void> update(@Valid @RequestBody SqlWhitelistRequest request) {
        whitelistService.update(request);
        return Result.success();
    }

    @Operation(summary = "删除")
    @DeleteMapping
    @PreAuthorize("hasAuthority('ai:sql-whitelist:remove')")
    @OperationLog(title = "AI SQL白名单", businessType = OperationLog.BusinessType.DELETE)
    public Result<Void> remove(@RequestBody List<Long> ids) {
        whitelistService.remove(ids);
        return Result.success();
    }
}
