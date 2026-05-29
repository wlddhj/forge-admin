package com.forge.modules.workflow.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.forge.framework.web.annotation.OperationLog;
import com.forge.common.response.PageResult;
import com.forge.common.response.Result;
import com.forge.modules.workflow.dto.model.ModelQueryRequest;
import com.forge.modules.workflow.dto.model.ModelRequest;
import com.forge.modules.workflow.dto.model.ModelResponse;
import com.forge.modules.workflow.service.WfModelService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * 模型管理控制器
 *
 * @author forge-admin
 */
@Tag(name = "模型管理")
@RestController
@RequestMapping("/workflow/model")
@RequiredArgsConstructor
public class WfModelController {

    private final WfModelService wfModelService;

    @Operation(summary = "分页查询模型")
    @GetMapping("/list")
    @PreAuthorize("hasAuthority('workflow:model:list')")
    public Result<PageResult<ModelResponse>> list(ModelQueryRequest request) {
        Page<ModelResponse> page = wfModelService.pageModel(request);
        PageResult<ModelResponse> result = PageResult.of(
                page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize()
        );
        return Result.success(result);
    }

    @Operation(summary = "获取模型详情")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('workflow:model:query')")
    public Result<ModelResponse> getInfo(@PathVariable String id) {
        return Result.success(wfModelService.getModelById(id));
    }

    @Operation(summary = "新增模型")
    @PostMapping
    @PreAuthorize("hasAuthority('workflow:model:add')")
    @OperationLog(title = "模型管理", businessType = OperationLog.BusinessType.INSERT)
    public Result<Void> add(@Valid @RequestBody ModelRequest request) {
        wfModelService.createModel(request);
        return Result.success();
    }

    @Operation(summary = "更新模型")
    @PutMapping
    @PreAuthorize("hasAuthority('workflow:model:edit')")
    @OperationLog(title = "模型管理", businessType = OperationLog.BusinessType.UPDATE)
    public Result<Void> edit(@Valid @RequestBody ModelRequest request) {
        wfModelService.updateModel(request);
        return Result.success();
    }

    @Operation(summary = "部署模型")
    @PostMapping("/{id}/deploy")
    @PreAuthorize("hasAuthority('workflow:model:deploy')")
    @OperationLog(title = "模型管理", businessType = OperationLog.BusinessType.INSERT)
    public Result<Void> deploy(@PathVariable String id) {
        wfModelService.deployModel(id);
        return Result.success();
    }

    @Operation(summary = "删除模型")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('workflow:model:delete')")
    @OperationLog(title = "模型管理", businessType = OperationLog.BusinessType.DELETE)
    public Result<Void> delete(@PathVariable String id) {
        wfModelService.deleteModel(id);
        return Result.success();
    }
}
