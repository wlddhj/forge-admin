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
}
