package com.forge.modules.workflow.controller.admin;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.forge.common.response.PageResult;
import com.forge.common.response.Result;
import com.forge.modules.workflow.dto.callback.CallbackRecordResponse;
import com.forge.modules.workflow.dto.callback.CallbackRequest;
import com.forge.modules.workflow.dto.callback.CallbackResponse;
import com.forge.modules.workflow.service.WfBusinessCallbackService;
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

/**
 * 业务回调管理
 *
 * @author forge-admin
 */
@Tag(name = "业务回调管理")
@RestController
@RequestMapping("/workflow/callback")
@RequiredArgsConstructor
public class WfBusinessCallbackController {

    private final WfBusinessCallbackService callbackService;

    @Operation(summary = "分页查询回调配置")
    @GetMapping
    @PreAuthorize("hasAuthority('workflow:callback:list')")
    public Result<PageResult<CallbackResponse>> list(@RequestParam(defaultValue = "1") int pageNum,
                                                     @RequestParam(defaultValue = "20") int pageSize,
                                                     @RequestParam(required = false) String name,
                                                     @RequestParam(required = false) Integer enabled) {
        Page<CallbackResponse> page = callbackService.pageCallback(pageNum, pageSize, name, enabled);
        return Result.success(PageResult.of(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize()));
    }

    @Operation(summary = "新增回调配置")
    @PostMapping
    @PreAuthorize("hasAuthority('workflow:callback:add')")
    public Result<Void> create(@Valid @RequestBody CallbackRequest request) {
        callbackService.createCallback(request);
        return Result.success(null);
    }

    @Operation(summary = "编辑回调配置")
    @PutMapping
    @PreAuthorize("hasAuthority('workflow:callback:edit')")
    public Result<Void> update(@Valid @RequestBody CallbackRequest request) {
        callbackService.updateCallback(request);
        return Result.success(null);
    }

    @Operation(summary = "删除回调配置")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('workflow:callback:delete')")
    public Result<Void> delete(@PathVariable Long id) {
        callbackService.deleteCallback(id);
        return Result.success(null);
    }

    @Operation(summary = "分页查询投递记录")
    @GetMapping("/{id}/records")
    @PreAuthorize("hasAuthority('workflow:callback:query')")
    public Result<PageResult<CallbackRecordResponse>> records(@PathVariable Long id,
                                                              @RequestParam(defaultValue = "1") int pageNum,
                                                              @RequestParam(defaultValue = "20") int pageSize,
                                                              @RequestParam(required = false) String status) {
        Page<CallbackRecordResponse> page = callbackService.pageRecords(pageNum, pageSize, id, status);
        return Result.success(PageResult.of(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize()));
    }

    @Operation(summary = "重发失败的投递记录")
    @PostMapping("/record/{recordId}/resend")
    @PreAuthorize("hasAuthority('workflow:callback:edit')")
    public Result<Void> resend(@PathVariable Long recordId) {
        callbackService.resendRecord(recordId);
        return Result.success(null);
    }
}
