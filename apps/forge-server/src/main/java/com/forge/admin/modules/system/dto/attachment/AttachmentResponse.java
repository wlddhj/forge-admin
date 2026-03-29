package com.forge.admin.modules.system.dto.attachment;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class AttachmentResponse {
    private Long id;
    private String fileName;
    private String originalName;
    private String filePath;
    private String fileUrl;
    private Long fileSize;
    private String fileType;
    private String fileExtension;
    private String storageType;
    private String bizType;
    private Long bizId;
    private Long uploaderId;
    private String uploaderName;
    private LocalDateTime createTime;
}
