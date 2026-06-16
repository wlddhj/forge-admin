package com.forge.modules.system.dto.app;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * App端上传响应
 *
 * @author forge
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AppUploadResponse {
    /**
     * 文件URL
     */
    private String url;

    /**
     * 附件ID
     */
    private Long attachmentId;
}