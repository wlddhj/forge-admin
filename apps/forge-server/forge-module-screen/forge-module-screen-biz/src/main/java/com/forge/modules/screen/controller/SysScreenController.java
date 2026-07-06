package com.forge.modules.screen.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.forge.common.response.PageResult;
import com.forge.common.response.Result;
import com.forge.framework.web.annotation.OperationLog;
import com.forge.modules.screen.dto.ScreenCopyRequest;
import com.forge.modules.screen.dto.ScreenPageRequest;
import com.forge.modules.screen.dto.ScreenRequest;
import com.forge.modules.screen.dto.ScreenResponse;
import com.forge.modules.screen.service.SysScreenService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 大屏管理 Controller。
 *
 * <p>提供大屏 CRUD、按 code 查询（运行时）、发布（草稿覆盖到正式）和复制能力。
 *
 * @author standadmin
 */
@Tag(name = "大屏管理")
@RestController
@RequestMapping("/screen")
@RequiredArgsConstructor
public class SysScreenController {

    private final SysScreenService sysScreenService;

    @Operation(summary = "分页查询大屏")
    @GetMapping("/list")
    @PreAuthorize("hasAuthority('screen:screen:list')")
    public Result<PageResult<ScreenResponse>> list(ScreenPageRequest request) {
        Page<ScreenResponse> page = sysScreenService.page(request);
        return Result.success(PageResult.of(page.getRecords(), page.getTotal(),
                page.getCurrent(), page.getSize()));
    }

    @Operation(summary = "查询单个大屏")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('screen:screen:query')")
    public Result<ScreenResponse> get(@PathVariable Long id) {
        return Result.success(sysScreenService.getById(id));
    }

    @Operation(summary = "按 code 查询大屏（运行时使用）")
    @GetMapping("/code/{code}")
    @PreAuthorize("hasAuthority('screen:screen:view:' + #code)")
    public Result<ScreenResponse> getByCode(@PathVariable String code) {
        return Result.success(sysScreenService.getByCode(code));
    }

    @Operation(summary = "新增大屏")
    @PostMapping
    @PreAuthorize("hasAuthority('screen:screen:add')")
    @OperationLog(title = "大屏管理", businessType = OperationLog.BusinessType.INSERT)
    public Result<Long> add(@Valid @RequestBody ScreenRequest request) {
        return Result.success(sysScreenService.create(request));
    }

    @Operation(summary = "修改大屏")
    @PutMapping
    @PreAuthorize("hasAuthority('screen:screen:edit')")
    @OperationLog(title = "大屏管理", businessType = OperationLog.BusinessType.UPDATE)
    public Result<Void> update(@Valid @RequestBody ScreenRequest request) {
        sysScreenService.update(request);
        return Result.success();
    }

    @Operation(summary = "删除大屏")
    @DeleteMapping
    @PreAuthorize("hasAuthority('screen:screen:remove')")
    @OperationLog(title = "大屏管理", businessType = OperationLog.BusinessType.DELETE)
    public Result<Void> delete(@RequestBody List<Long> ids) {
        sysScreenService.delete(ids);
        return Result.success();
    }

    @Operation(summary = "发布大屏")
    @PutMapping("/publish/{code}")
    @PreAuthorize("hasAuthority('screen:screen:publish')")
    @OperationLog(title = "大屏发布", businessType = OperationLog.BusinessType.UPDATE)
    public Result<Void> publish(@PathVariable String code) {
        sysScreenService.publish(code);
        return Result.success();
    }

    @Operation(summary = "复制大屏")
    @PostMapping("/copy/{code}")
    @PreAuthorize("hasAuthority('screen:screen:copy')")
    @OperationLog(title = "大屏复制", businessType = OperationLog.BusinessType.INSERT)
    public Result<Long> copy(@PathVariable String code,
                             @Valid @RequestBody ScreenCopyRequest request) {
        return Result.success(sysScreenService.copy(code, request));
    }
}
