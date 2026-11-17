package com.forge.modules.system.auth.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import java.util.List;

@Data
@ConfigurationProperties(prefix = "forge.security.upload")
public class UploadPolicyProperties {
    /** 文件大小上限（字节），默认 10MB */
    private long maxSize = 10L * 1024 * 1024;
    /** 允许的扩展名白名单 */
    private List<String> allowedExtensions = List.of(
        "jpg", "jpeg", "png", "gif", "webp", "bmp",
        "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx",
        "txt", "csv", "md", "json"
    );
    /** 是否启用 magic number 校验 */
    private boolean enableMagicCheck = true;

    /** 头像场景收紧策略（App/管理端头像上传在全局策略之上叠加） */
    private final Avatar avatar = new Avatar();

    @Data
    public static class Avatar {
        /** 头像大小上限（字节），默认 2MB */
        private long maxSize = 2L * 1024 * 1024;
        /** 头像允许的 MIME 类型 */
        private List<String> allowedContentTypes = List.of(
            "image/jpeg", "image/png", "image/webp");
    }
}
