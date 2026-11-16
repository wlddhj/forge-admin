package com.forge.modules.contract.controller.admin;

import com.forge.common.response.Result;
import com.forge.framework.web.annotation.OperationLog;
import com.forge.modules.contract.dto.CategoryDtos;
import com.forge.modules.contract.service.ContractCategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 合同分类管理（树形）
 *
 * @author forge-admin
 */
@Tag(name = "合同分类管理")
@RestController
@RequestMapping("/contract/category")
@RequiredArgsConstructor
public class ContractCategoryController {

    private final ContractCategoryService categoryService;

    @Operation(summary = "分类树（status 筛选）")
    @GetMapping("/list")
    @PreAuthorize("hasAuthority('contract:category:list')")
    public Result<List<CategoryDtos.TreeNode>> list(@RequestParam(required = false) Integer status) {
        return Result.success(categoryService.tree(status));
    }

    @Operation(summary = "全量树（表单下拉）")
    @GetMapping("/all")
    @PreAuthorize("hasAuthority('contract:category:list')")
    public Result<List<CategoryDtos.TreeNode>> all() {
        return Result.success(categoryService.treeAll());
    }

    @Operation(summary = "新增分类")
    @PostMapping
    @PreAuthorize("hasAuthority('contract:category:create')")
    @OperationLog(title = "合同分类管理", businessType = OperationLog.BusinessType.INSERT)
    public Result<Long> add(@Valid @RequestBody CategoryDtos.SaveRequest request) {
        return Result.success(categoryService.addCategory(request));
    }

    @Operation(summary = "编辑分类")
    @PutMapping
    @PreAuthorize("hasAuthority('contract:category:update')")
    @OperationLog(title = "合同分类管理", businessType = OperationLog.BusinessType.UPDATE)
    public Result<Void> edit(@Valid @RequestBody CategoryDtos.SaveRequest request) {
        categoryService.updateCategory(request);
        return Result.success();
    }

    @Operation(summary = "删除分类（有子分类或模板挂载禁删）")
    @DeleteMapping("/{ids}")
    @PreAuthorize("hasAuthority('contract:category:delete')")
    @OperationLog(title = "合同分类管理", businessType = OperationLog.BusinessType.DELETE)
    public Result<Void> remove(@PathVariable List<Long> ids) {
        categoryService.deleteCategory(ids);
        return Result.success();
    }
}
