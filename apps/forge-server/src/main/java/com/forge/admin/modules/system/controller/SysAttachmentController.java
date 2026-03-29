package com.forge.admin.modules.system.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.forge.admin.common.annotation.OperationLog;
import com.forge.admin.common.response.PageResult;
import com.forge.admin.common.response.Result;
import com.forge.admin.modules.system.dto.attachment.AttachmentQueryRequest;
import com.forge.admin.modules.system.dto.attachment.AttachmentResponse;
import com.forge.admin.modules.system.service.SysAttachmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 附件管理控制器
 */
@Tag(name = "附件管理")
@RestController
@RequestMapping("/system/attachment")
@RequiredArgsConstructor
public class SysAttachmentController {

    private final SysAttachmentService sysAttachmentService;

    @Operation(summary = "分页查询附件")
    @GetMapping("/list")
    @PreAuthorize("hasAuthority('system:attachment:list')")
    public Result<PageResult<AttachmentResponse>> list(AttachmentQueryRequest request) {
        Page<AttachmentResponse> page = sysAttachmentService.pageAttachments(request);
        PageResult<AttachmentResponse> result = PageResult.of(
                page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize()
        );
        return Result.success(result);
    }

    @Operation(summary = "获取附件详情")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('system:attachment:query')")
    public Result<AttachmentResponse> getInfo(@PathVariable Long id) {
        return Result.success(sysAttachmentService.getAttachmentDetail(id));
    }

    @Operation(summary = "上传附件")
    @PostMapping("/upload")
    @PreAuthorize("hasAuthority('system:attachment:upload')")
    @OperationLog(title = "附件管理", businessType = OperationLog.BusinessType.INSERT)
    public Result<AttachmentResponse> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "bizType", required = false) String bizType,
            @RequestParam(value = "bizId", required = false) Long bizId) {
        return Result.success(sysAttachmentService.upload(file, bizType, bizId));
    }

    @Operation(summary = "删除附件")
    @DeleteMapping("/{ids}")
    @PreAuthorize("hasAuthority('system:attachment:delete')")
    @OperationLog(title = "附件管理", businessType = OperationLog.BusinessType.DELETE)
    public Result<Void> delete(@PathVariable List<Long> ids) {
        sysAttachmentService.deleteAttachments(ids);
        return Result.success();
    }

    @Operation(summary = "上传头像")
    @PostMapping("/avatar")
    @OperationLog(title = "个人中心", businessType = OperationLog.BusinessType.UPDATE)
    public Result<String> uploadAvatar(@RequestParam("file") MultipartFile file) {
        return Result.success(sysAttachmentService.uploadAvatar(file));
    }
}
