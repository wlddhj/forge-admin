package com.forge.modules.system.dto.app;

import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(description = "App端上传响应")
public class AppUploadResponse {
    /**
     * 文件URL
     */
    @Schema(description = "文件URL")
    private String url;

    /**
     * 附件ID
     */
    @Schema(description = "附件ID")
    private Long attachmentId;
}