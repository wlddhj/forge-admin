package com.forge.modules.workflow.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.forge.framework.web.annotation.OperationLog;
import com.forge.common.response.PageResult;
import com.forge.common.response.Result;
import com.forge.modules.workflow.dto.category.CategoryQueryRequest;
import com.forge.modules.workflow.dto.category.CategoryRequest;
import com.forge.modules.workflow.dto.category.CategoryResponse;
import com.forge.modules.workflow.entity.WfCategory;
import com.forge.modules.workflow.service.WfCategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 流程分类控制器
 *
 * @author forge
 */
@Tag(name = "流程分类管理")
@RestController
@RequestMapping("/workflow/category")
@RequiredArgsConstructor
public class WfCategoryController {

    private final WfCategoryService wfCategoryService;

    @Operation(summary = "分页查询流程分类")
    @GetMapping("/list")
    @PreAuthorize("hasAuthority('workflow:category:list')")
    public Result<PageResult<WfCategory>> list(CategoryQueryRequest request) {
        Page<WfCategory> page = wfCategoryService.pageCategory(request);
        PageResult<WfCategory> result = PageResult.of(
                page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize()
        );
        return Result.success(result);
    }

    @Operation(summary = "获取所有启用的分类")
    @GetMapping("/all")
    public Result<List<CategoryResponse>> all() {
        return Result.success(wfCategoryService.listAll());
    }

    @Operation(summary = "获取分类详情")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('workflow:category:query')")
    public Result<WfCategory> getInfo(@PathVariable Long id) {
        return Result.success(wfCategoryService.getCategoryById(id));
    }

    @Operation(summary = "新增流程分类")
    @PostMapping
    @PreAuthorize("hasAuthority('workflow:category:add')")
    @OperationLog(title = "流程分类管理", businessType = OperationLog.BusinessType.INSERT)
    public Result<Void> add(@Valid @RequestBody CategoryRequest request) {
        wfCategoryService.addCategory(request);
        return Result.success();
    }

    @Operation(summary = "更新流程分类")
    @PutMapping
    @PreAuthorize("hasAuthority('workflow:category:edit')")
    @OperationLog(title = "流程分类管理", businessType = OperationLog.BusinessType.UPDATE)
    public Result<Void> edit(@Valid @RequestBody CategoryRequest request) {
        wfCategoryService.updateCategory(request);
        return Result.success();
    }

    @Operation(summary = "删除流程分类")
    @DeleteMapping("/{ids}")
    @PreAuthorize("hasAuthority('workflow:category:delete')")
    @OperationLog(title = "流程分类管理", businessType = OperationLog.BusinessType.DELETE)
    public Result<Void> delete(@PathVariable List<Long> ids) {
        wfCategoryService.deleteCategory(ids);
        return Result.success();
    }
}
