package com.forge.admin.modules.system.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.forge.admin.common.annotation.OperationLog;
import com.forge.admin.common.response.PageResult;
import com.forge.admin.common.response.Result;
import com.forge.admin.modules.system.dto.dict.DictDataQueryRequest;
import com.forge.admin.modules.system.dto.dict.DictDataRequest;
import com.forge.admin.modules.system.dto.dict.DictDataResponse;
import com.forge.admin.modules.system.service.SysDictDataService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 字典数据控制器
 */
@Tag(name = "字典数据管理")
@RestController
@RequestMapping("/system/dict-data")
@RequiredArgsConstructor
public class SysDictDataController {

    private final SysDictDataService sysDictDataService;

    @Operation(summary = "分页查询字典数据")
    @GetMapping("/list")
    @PreAuthorize("hasAuthority('system:dict:list')")
    public Result<PageResult<DictDataResponse>> list(DictDataQueryRequest request) {
        Page<DictDataResponse> page = sysDictDataService.pageDictData(request);
        PageResult<DictDataResponse> result = PageResult.of(
                page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize()
        );
        return Result.success(result);
    }

    @Operation(summary = "根据字典类型获取字典数据")
    @GetMapping("/type/{dictType}")
    public Result<List<DictDataResponse>> getByType(@PathVariable String dictType) {
        return Result.success(sysDictDataService.getDictDataByType(dictType));
    }

    @Operation(summary = "获取字典数据详情")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('system:dict:query')")
    public Result<DictDataResponse> getInfo(@PathVariable Long id) {
        return Result.success(sysDictDataService.getDictDataDetail(id));
    }

    @Operation(summary = "新增字典数据")
    @PostMapping
    @PreAuthorize("hasAuthority('system:dict:add')")
    @OperationLog(title = "字典数据管理", businessType = OperationLog.BusinessType.INSERT)
    public Result<Void> add(@Valid @RequestBody DictDataRequest request) {
        sysDictDataService.addDictData(request);
        return Result.success();
    }

    @Operation(summary = "更新字典数据")
    @PutMapping
    @PreAuthorize("hasAuthority('system:dict:edit')")
    @OperationLog(title = "字典数据管理", businessType = OperationLog.BusinessType.UPDATE)
    public Result<Void> edit(@Valid @RequestBody DictDataRequest request) {
        sysDictDataService.updateDictData(request);
        return Result.success();
    }

    @Operation(summary = "删除字典数据")
    @DeleteMapping("/{ids}")
    @PreAuthorize("hasAuthority('system:dict:delete')")
    @OperationLog(title = "字典数据管理", businessType = OperationLog.BusinessType.DELETE)
    public Result<Void> delete(@PathVariable List<Long> ids) {
        sysDictDataService.deleteDictData(ids);
        return Result.success();
    }

    @Operation(summary = "更新字典数据状态")
    @PutMapping("/{id}/status")
    @PreAuthorize("hasAuthority('system:dict:edit')")
    @OperationLog(title = "字典数据管理", businessType = OperationLog.BusinessType.UPDATE)
    public Result<Void> updateStatus(@PathVariable Long id, @RequestParam Integer status) {
        sysDictDataService.updateStatus(id, status);
        return Result.success();
    }
}
