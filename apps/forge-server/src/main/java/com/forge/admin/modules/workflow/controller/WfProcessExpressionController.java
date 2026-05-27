package com.forge.admin.modules.workflow.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.forge.admin.common.annotation.OperationLog;
import com.forge.admin.common.response.PageResult;
import com.forge.admin.common.response.Result;
import com.forge.admin.modules.workflow.dto.expression.ExpressionQueryRequest;
import com.forge.admin.modules.workflow.dto.expression.ExpressionRequest;
import com.forge.admin.modules.workflow.dto.expression.ExpressionResponse;
import com.forge.admin.modules.workflow.service.WfProcessExpressionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "流程表达式管理")
@RestController
@RequestMapping("/workflow/expression")
@RequiredArgsConstructor
public class WfProcessExpressionController {

    private final WfProcessExpressionService expressionService;

    @Operation(summary = "分页查询表达式")
    @GetMapping("/list")
    @PreAuthorize("hasAuthority('workflow:expression:list')")
    public Result<PageResult<ExpressionResponse>> list(ExpressionQueryRequest request) {
        Page<ExpressionResponse> page = expressionService.pageExpressions(request);
        return Result.success(PageResult.of(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize()));
    }

    @Operation(summary = "获取所有启用的表达式")
    @GetMapping("/all")
    public Result<List<ExpressionResponse>> all() {
        return Result.success(expressionService.listAllEnabled());
    }

    @Operation(summary = "获取表达式详情")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('workflow:expression:query')")
    public Result<ExpressionResponse> getInfo(@PathVariable Long id) {
        return Result.success(expressionService.getExpressionDetail(id));
    }

    @Operation(summary = "新增表达式")
    @PostMapping
    @PreAuthorize("hasAuthority('workflow:expression:add')")
    @OperationLog(title = "流程表达式管理", businessType = OperationLog.BusinessType.INSERT)
    public Result<Void> add(@Valid @RequestBody ExpressionRequest request) {
        expressionService.addExpression(request);
        return Result.success();
    }

    @Operation(summary = "更新表达式")
    @PutMapping
    @PreAuthorize("hasAuthority('workflow:expression:edit')")
    @OperationLog(title = "流程表达式管理", businessType = OperationLog.BusinessType.UPDATE)
    public Result<Void> edit(@Valid @RequestBody ExpressionRequest request) {
        expressionService.updateExpression(request);
        return Result.success();
    }

    @Operation(summary = "删除表达式")
    @DeleteMapping
    @PreAuthorize("hasAuthority('workflow:expression:delete')")
    @OperationLog(title = "流程表达式管理", businessType = OperationLog.BusinessType.DELETE)
    public Result<Void> delete(@RequestBody List<Long> ids) {
        expressionService.deleteExpressions(ids);
        return Result.success();
    }
}
