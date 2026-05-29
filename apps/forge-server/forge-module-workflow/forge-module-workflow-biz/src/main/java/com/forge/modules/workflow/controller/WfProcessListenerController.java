package com.forge.modules.workflow.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.forge.framework.web.annotation.OperationLog;
import com.forge.common.response.PageResult;
import com.forge.common.response.Result;
import com.forge.modules.workflow.dto.listener.ListenerQueryRequest;
import com.forge.modules.workflow.dto.listener.ListenerRequest;
import com.forge.modules.workflow.dto.listener.ListenerResponse;
import com.forge.modules.workflow.service.WfProcessListenerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "流程监听器管理")
@RestController
@RequestMapping("/workflow/listener")
@RequiredArgsConstructor
public class WfProcessListenerController {

    private final WfProcessListenerService listenerService;

    @Operation(summary = "分页查询监听器")
    @GetMapping("/list")
    @PreAuthorize("hasAuthority('workflow:listener:list')")
    public Result<PageResult<ListenerResponse>> list(ListenerQueryRequest request) {
        Page<ListenerResponse> page = listenerService.pageListeners(request);
        return Result.success(PageResult.of(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize()));
    }

    @Operation(summary = "获取所有启用的监听器")
    @GetMapping("/all")
    public Result<List<ListenerResponse>> all() {
        return Result.success(listenerService.listAllEnabled());
    }

    @Operation(summary = "获取监听器详情")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('workflow:listener:query')")
    public Result<ListenerResponse> getInfo(@PathVariable Long id) {
        return Result.success(listenerService.getListenerDetail(id));
    }

    @Operation(summary = "新增监听器")
    @PostMapping
    @PreAuthorize("hasAuthority('workflow:listener:add')")
    @OperationLog(title = "流程监听器管理", businessType = OperationLog.BusinessType.INSERT)
    public Result<Void> add(@Valid @RequestBody ListenerRequest request) {
        listenerService.addListener(request);
        return Result.success();
    }

    @Operation(summary = "更新监听器")
    @PutMapping
    @PreAuthorize("hasAuthority('workflow:listener:edit')")
    @OperationLog(title = "流程监听器管理", businessType = OperationLog.BusinessType.UPDATE)
    public Result<Void> edit(@Valid @RequestBody ListenerRequest request) {
        listenerService.updateListener(request);
        return Result.success();
    }

    @Operation(summary = "删除监听器")
    @DeleteMapping
    @PreAuthorize("hasAuthority('workflow:listener:delete')")
    @OperationLog(title = "流程监听器管理", businessType = OperationLog.BusinessType.DELETE)
    public Result<Void> delete(@RequestBody List<Long> ids) {
        listenerService.deleteListeners(ids);
        return Result.success();
    }
}
