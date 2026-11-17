package com.forge.modules.system.controller.app;

import com.forge.common.response.Result;
import com.forge.framework.web.annotation.RepeatSubmit;
import com.forge.modules.system.annotation.AssertAppUserActive;
import com.forge.modules.system.auth.properties.UploadPolicyProperties;
import com.forge.modules.system.auth.util.FileUploadValidator;
import com.forge.modules.system.constant.AttachmentBizTypes;
import com.forge.modules.system.dto.app.AppUploadResponse;
import com.forge.modules.system.dto.attachment.AttachmentResponse;
import com.forge.modules.system.service.SysAttachmentService;
import com.forge.modules.system.service.storage.AttachmentUrls;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

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
    private final FileUploadValidator fileUploadValidator;
    private final UploadPolicyProperties uploadPolicyProperties;

    @Operation(summary = "上传头像")
    @PostMapping("/upload")
    @AssertAppUserActive
    @RepeatSubmit
    public Result<AppUploadResponse> uploadAvatar(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "bizType", defaultValue = AttachmentBizTypes.AVATAR) String bizType) {

        // 全量校验（大小/扩展名/Magic Number，forge.security.upload 全局策略）
        String validationError = fileUploadValidator.validate(file);
        if (validationError != null) {
            return Result.failed(5801, validationError);
        }

        // 头像场景收紧（forge.security.upload.avatar 策略：MIME 白名单 + 更小尺寸上限）
        UploadPolicyProperties.Avatar avatarPolicy = uploadPolicyProperties.getAvatar();
        if (!avatarPolicy.getAllowedContentTypes().contains(file.getContentType())) {
            return Result.failed(5801, "仅支持 JPG/PNG/WebP 格式");
        }
        if (file.getSize() > avatarPolicy.getMaxSize()) {
            return Result.failed(5802, "文件大小不能超过 " + (avatarPolicy.getMaxSize() / 1024 / 1024) + "MB");
        }

        // 复用 SysAttachmentService
        AttachmentResponse response = sysAttachmentService.upload(file, bizType, null);

        return Result.success(AppUploadResponse.builder()
                .url(AttachmentUrls.appPublicUrl(response.getFileUrl()))
                .attachmentId(response.getId())
                .build());
    }
}
