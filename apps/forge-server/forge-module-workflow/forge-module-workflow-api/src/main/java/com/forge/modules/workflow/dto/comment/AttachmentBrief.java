package com.forge.modules.workflow.dto.comment;

import lombok.Data;

/**
 * 审批评论附件简要信息
 *
 * @author forge-admin
 */
@Data
public class AttachmentBrief {

    /**
     * 附件ID
     */
    private Long id;

    /**
     * 原始文件名
     */
    private String originalName;

    /**
     * 访问URL
     */
    private String fileUrl;
}
