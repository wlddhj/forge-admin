package com.forge.modules.system.controller.app;

import com.forge.common.response.Result;
import com.forge.modules.system.annotation.AssertAppUserActive;
import com.forge.modules.system.dto.app.AppUploadResponse;
import com.forge.modules.system.dto.attachment.AttachmentResponse;
import com.forge.modules.system.service.SysAttachmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Set;

/**
 * App端附件控制器
 *
 * @author forge
 */
@Tag(name = "移动端 - 附件")
@RestController
@RequestMapping("/attachment")
@RequiredArgsConstructor
public class AppAttachmentController {

    private final SysAttachmentService sysAttachmentService;

    private static final long MAX_SIZE = 2 * 1024 * 1024; // 2MB
    private static final Set<String> ALLOWED_TYPES = Set.of(
            "image/jpeg", "image/png", "image/webp");

    @Operation(summary = "上传头像")
    @PostMapping("/upload")
    @AssertAppUserActive
    public Result<AppUploadResponse> uploadAvatar(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "bizType", defaultValue = "APP_AVATAR") String bizType) {

        // 校验类型
        String contentType = file.getContentType();
        if (!ALLOWED_TYPES.contains(contentType)) {
            return Result.failed(5801, "仅支持 JPG/PNG/WebP 格式");
        }

        // 校验大小
        if (file.getSize() > MAX_SIZE) {
            return Result.failed(5802, "文件大小不能超过 2MB");
        }

        // 复用 SysAttachmentService
        AttachmentResponse response = sysAttachmentService.upload(file, bizType, null);

        return Result.success(AppUploadResponse.builder()
                .url(response.getFileUrl())
                .attachmentId(response.getId())
                .build());
    }
}