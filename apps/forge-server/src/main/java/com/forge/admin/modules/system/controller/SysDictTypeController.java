package com.forge.admin.modules.system.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.forge.admin.common.annotation.OperationLog;
import com.forge.admin.common.response.PageResult;
import com.forge.admin.common.response.Result;
import com.forge.admin.modules.system.dto.dict.DictTypeQueryRequest;
import com.forge.admin.modules.system.dto.dict.DictTypeRequest;
import com.forge.admin.modules.system.dto.dict.DictTypeResponse;
import com.forge.admin.modules.system.service.SysDictDataService;
import com.forge.admin.modules.system.service.SysDictTypeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 字典类型控制器
 */
@Tag(name = "字典类型管理")
@RestController
@RequestMapping("/system/dict-type")
@RequiredArgsConstructor
public class SysDictTypeController {

    private final SysDictTypeService sysDictTypeService;
    private final SysDictDataService sysDictDataService;

    @Operation(summary = "分页查询字典类型")
    @GetMapping("/list")
    @PreAuthorize("hasAuthority('system:dict:list')")
    public Result<PageResult<DictTypeResponse>> list(DictTypeQueryRequest request) {
        Page<DictTypeResponse> page = sysDictTypeService.pageDictTypes(request);
        PageResult<DictTypeResponse> result = PageResult.of(
                page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize()
        );
        return Result.success(result);
    }

    @Operation(summary = "获取所有字典类型")
    @GetMapping("/all")
    public Result<List<DictTypeResponse>> all() {
        return Result.success(sysDictTypeService.getAllDictTypes());
    }

    @Operation(summary = "获取字典类型详情")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('system:dict:query')")
    public Result<DictTypeResponse> getInfo(@PathVariable Long id) {
        return Result.success(sysDictTypeService.getDictTypeDetail(id));
    }

    @Operation(summary = "新增字典类型")
    @PostMapping
    @PreAuthorize("hasAuthority('system:dict:add')")
    @OperationLog(title = "字典类型管理", businessType = OperationLog.BusinessType.INSERT)
    public Result<Void> add(@Valid @RequestBody DictTypeRequest request) {
        sysDictTypeService.addDictType(request);
        return Result.success();
    }

    @Operation(summary = "更新字典类型")
    @PutMapping
    @PreAuthorize("hasAuthority('system:dict:edit')")
    @OperationLog(title = "字典类型管理", businessType = OperationLog.BusinessType.UPDATE)
    public Result<Void> edit(@Valid @RequestBody DictTypeRequest request) {
        sysDictTypeService.updateDictType(request);
        return Result.success();
    }

    @Operation(summary = "删除字典类型")
    @DeleteMapping("/{ids}")
    @PreAuthorize("hasAuthority('system:dict:delete')")
    @OperationLog(title = "字典类型管理", businessType = OperationLog.BusinessType.DELETE)
    public Result<Void> delete(@PathVariable List<Long> ids) {
        sysDictTypeService.deleteDictTypes(ids);
        return Result.success();
    }

    @Operation(summary = "更新字典类型状态")
    @PutMapping("/{id}/status")
    @PreAuthorize("hasAuthority('system:dict:edit')")
    @OperationLog(title = "字典类型管理", businessType = OperationLog.BusinessType.UPDATE)
    public Result<Void> updateStatus(@PathVariable Long id, @RequestParam Integer status) {
        sysDictTypeService.updateStatus(id, status);
        return Result.success();
    }

    @Operation(summary = "刷新字典缓存")
    @DeleteMapping("/cache")
    @PreAuthorize("hasAuthority('system:dict:edit')")
    @OperationLog(title = "字典类型管理", businessType = OperationLog.BusinessType.CLEAN)
    public Result<Void> refreshCache() {
        sysDictDataService.refreshCache();
        return Result.success();
    }
}
