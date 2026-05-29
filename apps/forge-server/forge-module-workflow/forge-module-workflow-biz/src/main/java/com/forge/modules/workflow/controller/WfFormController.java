package com.forge.modules.workflow.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.forge.framework.web.annotation.OperationLog;
import com.forge.common.response.PageResult;
import com.forge.common.response.Result;
import com.forge.modules.workflow.dto.form.FormQueryRequest;
import com.forge.modules.workflow.dto.form.FormRequest;
import com.forge.modules.workflow.dto.form.FormResponse;
import com.forge.modules.workflow.dto.form.FormSimpleResponse;
import com.forge.modules.workflow.service.WfFormService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 表单管理控制器
 *
 * @author forge
 */
@Tag(name = "表单管理")
@RestController
@RequestMapping("/workflow/form")
@RequiredArgsConstructor
public class WfFormController {

    private final WfFormService wfFormService;

    @Operation(summary = "分页查询表单")
    @GetMapping("/list")
    @PreAuthorize("hasAuthority('workflow:form:list')")
    public Result<PageResult<FormResponse>> list(FormQueryRequest request) {
        Page<FormResponse> page = wfFormService.pageForms(request);
        PageResult<FormResponse> result = PageResult.of(
                page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize()
        );
        return Result.success(result);
    }

    @Operation(summary = "获取所有启用的表单（下拉选择）")
    @GetMapping("/all")
    public Result<List<FormSimpleResponse>> all() {
        return Result.success(wfFormService.listAllSimple());
    }

    @Operation(summary = "获取表单详情")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('workflow:form:query')")
    public Result<FormResponse> getInfo(@PathVariable Long id) {
        return Result.success(wfFormService.getFormDetail(id));
    }

    @Operation(summary = "新增表单")
    @PostMapping
    @PreAuthorize("hasAuthority('workflow:form:add')")
    @OperationLog(title = "表单管理", businessType = OperationLog.BusinessType.INSERT)
    public Result<Void> add(@Valid @RequestBody FormRequest request) {
        wfFormService.addForm(request);
        return Result.success();
    }

    @Operation(summary = "更新表单")
    @PutMapping
    @PreAuthorize("hasAuthority('workflow:form:edit')")
    @OperationLog(title = "表单管理", businessType = OperationLog.BusinessType.UPDATE)
    public Result<Void> edit(@Valid @RequestBody FormRequest request) {
        wfFormService.updateForm(request);
        return Result.success();
    }

    @Operation(summary = "删除表单")
    @DeleteMapping
    @PreAuthorize("hasAuthority('workflow:form:delete')")
    @OperationLog(title = "表单管理", businessType = OperationLog.BusinessType.DELETE)
    public Result<Void> delete(@RequestBody List<Long> ids) {
        wfFormService.deleteForms(ids);
        return Result.success();
    }
}
