package com.forge.modules.system.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.forge.common.response.PageResult;
import com.forge.common.response.Result;
import com.forge.framework.web.annotation.OperationLog;
import com.forge.modules.system.dto.keysequence.KeySequenceQueryRequest;
import com.forge.modules.system.dto.keysequence.KeySequenceRequest;
import com.forge.modules.system.dto.keysequence.KeySequenceResponse;
import com.forge.modules.system.service.KeySequenceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Tag(name = "序列号生成器")
@RestController
@RequestMapping("/system/key-sequence")
@RequiredArgsConstructor
public class KeySequenceController {

    private final KeySequenceService keySequenceService;

    @Operation(summary = "分页查询")
    @GetMapping("/list")
    @PreAuthorize("hasAuthority('system:key-sequence:list')")
    public Result<PageResult<KeySequenceResponse>> list(KeySequenceQueryRequest request) {
        Page<KeySequenceResponse> page = keySequenceService.pageKeySequences(request);
        return Result.success(PageResult.of(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize()));
    }

    @Operation(summary = "获取详情")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('system:key-sequence:query')")
    public Result<KeySequenceResponse> getInfo(@PathVariable Long id) {
        return Result.success(keySequenceService.getKeySequenceDetail(id));
    }

    @Operation(summary = "新增")
    @PostMapping
    @PreAuthorize("hasAuthority('system:key-sequence:add')")
    @OperationLog(title = "序列号生成器", businessType = OperationLog.BusinessType.INSERT)
    public Result<Void> add(@Valid @RequestBody KeySequenceRequest request) {
        keySequenceService.addKeySequence(request);
        return Result.success();
    }

    @Operation(summary = "更新")
    @PutMapping
    @PreAuthorize("hasAuthority('system:key-sequence:edit')")
    @OperationLog(title = "序列号生成器", businessType = OperationLog.BusinessType.UPDATE)
    public Result<Void> edit(@Valid @RequestBody KeySequenceRequest request) {
        keySequenceService.updateKeySequence(request);
        return Result.success();
    }

    @Operation(summary = "删除")
    @DeleteMapping("/{ids}")
    @PreAuthorize("hasAuthority('system:key-sequence:delete')")
    @OperationLog(title = "序列号生成器", businessType = OperationLog.BusinessType.DELETE)
    public Result<Void> delete(@PathVariable Long[] ids) {
        keySequenceService.deleteKeySequences(ids);
        return Result.success();
    }
}
