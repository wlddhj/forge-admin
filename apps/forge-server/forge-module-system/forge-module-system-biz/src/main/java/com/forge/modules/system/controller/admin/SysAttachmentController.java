package com.forge.modules.system.controller.admin;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.forge.common.utils.SecurityUtils;
import com.forge.framework.web.annotation.OperationLog;
import com.forge.framework.web.annotation.RepeatSubmit;
import com.forge.common.response.PageResult;
import com.forge.common.response.Result;
import com.forge.modules.system.constant.AttachmentBizTypes;
import com.forge.modules.system.dto.attachment.AttachmentContent;
import com.forge.modules.system.dto.attachment.AttachmentQueryRequest;
import com.forge.modules.system.dto.attachment.AttachmentResponse;
import com.forge.modules.system.service.SysAttachmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
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

    @Operation(summary = "按业务查询附件列表")
    @GetMapping("/list-by-biz")
    @PreAuthorize("hasAuthority('system:attachment:query')")
    public Result<List<AttachmentResponse>> listByBiz(@RequestParam String bizType, @RequestParam Long bizId) {
        return Result.success(sysAttachmentService.listByBiz(bizType, bizId));
    }

    @Operation(summary = "上传附件")
    @PostMapping("/upload")
    @PreAuthorize("hasAuthority('system:attachment:upload')")
    @OperationLog(title = "附件管理", businessType = OperationLog.BusinessType.INSERT)
    @RepeatSubmit
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
    @RepeatSubmit
    public Result<Void> delete(@PathVariable List<Long> ids) {
        sysAttachmentService.deleteAttachments(ids);
        return Result.success();
    }

    // 权限语义：登录即可下载（无 @PreAuthorize）——与 avatar 上传同口径；需要严控的 bizType
    // 通过 AttachmentAccessChecker（service/spi）逐类型接入，未注册校验器的类型默认放行
    @Operation(summary = "下载附件内容（流式代理，登录即可）")
    @GetMapping("/{id}/download")
    public ResponseEntity<InputStreamResource> download(@PathVariable Long id) {
        AttachmentContent content = sysAttachmentService.openStream(id, SecurityUtils.getCurrentUserId());
        String contentType = StringUtils.hasText(content.contentType())
                ? content.contentType() : MediaType.APPLICATION_OCTET_STREAM_VALUE;
        ResponseEntity.BodyBuilder builder = ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, contentType)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(content.fileName() == null ? "attachment" : content.fileName(), StandardCharsets.UTF_8)
                        .build()
                        .toString());
        if (content.fileSize() != null && content.fileSize() > 0) {
            // 显式设置长度：避免 Spring 对 InputStreamResource 读流探测长度
            builder.contentLength(content.fileSize());
        }
        return builder.body(new InputStreamResource(content.stream()));
    }

    // 权限语义：登录即可上传（无 @PreAuthorize）——任何角色都有头像，不应要求附件管理权限；
    // 入口收紧由 FileUploadValidator + 头像场景策略兜底，落库仍统一走附件表
    @Operation(summary = "上传头像")
    @PostMapping("/avatar")
    @OperationLog(title = "个人中心", businessType = OperationLog.BusinessType.UPDATE)
    @RepeatSubmit
    public Result<AttachmentResponse> uploadAvatar(@RequestParam("file") MultipartFile file) {
        return Result.success(sysAttachmentService.upload(file, AttachmentBizTypes.AVATAR, null));
    }
}
