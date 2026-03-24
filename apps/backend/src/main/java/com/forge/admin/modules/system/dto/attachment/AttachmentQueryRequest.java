package com.forge.admin.modules.system.dto.attachment;

import lombok.Data;

@Data
public class AttachmentQueryRequest {
    private String fileName;
    private String fileType;
    private String storageType;
    private String uploaderName;
    private String startTime;
    private String endTime;
    private Integer pageNum = 1;
    private Integer pageSize = 10;
}
