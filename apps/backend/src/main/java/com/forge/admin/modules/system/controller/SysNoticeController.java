package com.forge.admin.modules.system.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.forge.admin.common.annotation.OperationLog;
import com.forge.admin.common.response.PageResult;
import com.forge.admin.common.response.Result;
import com.forge.admin.modules.system.dto.notice.NoticeQueryRequest;
import com.forge.admin.modules.system.dto.notice.NoticeRequest;
import com.forge.admin.modules.system.dto.notice.NoticeResponse;
import com.forge.admin.modules.system.service.SysNoticeService;
import com.forge.admin.common.utils.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 通知公告控制器
 */
@Tag(name = "通知公告管理")
@RestController
@RequestMapping("/system/notice")
@RequiredArgsConstructor
public class SysNoticeController {

    private final SysNoticeService sysNoticeService;

    @Operation(summary = "分页查询通知公告")
    @GetMapping("/list")
    @PreAuthorize("hasAuthority('system:notice:list')")
    public Result<PageResult<NoticeResponse>> list(NoticeQueryRequest request) {
        Page<NoticeResponse> page = sysNoticeService.pageNotices(request);
        PageResult<NoticeResponse> result = PageResult.of(
                page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize()
        );
        return Result.success(result);
    }

    @Operation(summary = "获取最新公告")
    @GetMapping("/latest")
    public Result<List<NoticeResponse>> latest(@RequestParam(required = false, defaultValue = "5") Integer limit) {
        return Result.success(sysNoticeService.getLatestNotices(limit));
    }

    @Operation(summary = "获取公告详情")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('system:notice:query')")
    public Result<NoticeResponse> getInfo(@PathVariable Long id) {
        return Result.success(sysNoticeService.getNoticeDetail(id));
    }

    @Operation(summary = "新增公告")
    @PostMapping
    @PreAuthorize("hasAuthority('system:notice:add')")
    @OperationLog(title = "通知公告管理", businessType = OperationLog.BusinessType.INSERT)
    public Result<Void> add(@Valid @RequestBody NoticeRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        sysNoticeService.addNotice(request, userId);
        return Result.success();
    }

    @Operation(summary = "更新公告")
    @PutMapping
    @PreAuthorize("hasAuthority('system:notice:edit')")
    @OperationLog(title = "通知公告管理", businessType = OperationLog.BusinessType.UPDATE)
    public Result<Void> edit(@Valid @RequestBody NoticeRequest request) {
        sysNoticeService.updateNotice(request);
        return Result.success();
    }

    @Operation(summary = "删除公告")
    @DeleteMapping("/{ids}")
    @PreAuthorize("hasAuthority('system:notice:delete')")
    @OperationLog(title = "通知公告管理", businessType = OperationLog.BusinessType.DELETE)
    public Result<Void> delete(@PathVariable Long[] ids) {
        sysNoticeService.deleteNotices(ids);
        return Result.success();
    }
}
