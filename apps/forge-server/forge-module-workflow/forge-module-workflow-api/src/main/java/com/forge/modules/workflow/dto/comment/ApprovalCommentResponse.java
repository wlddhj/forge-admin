package com.forge.modules.workflow.dto.comment;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Date;

/**
 * 审批意见响应
 *
 * @author forge-admin
 */
@Data
public class ApprovalCommentResponse {

    /**
     * ID
     */
    private Long id;

    /**
     * 流程实例ID
     */
    private String processInstanceId;

    /**
     * 任务ID
     */
    private String taskId;

    /**
     * 任务定义Key
     */
    private String taskDefKey;

    /**
     * 任务名称
     */
    private String taskName;

    /**
     * 审批人ID
     */
    private Long userId;

    /**
     * 审批人名称
     */
    private String userName;

    /**
     * 操作类型
     */
    private String actionType;

    /**
     * 审批意见
     */
    private String commentText;

    /**
     * 附件ID列表
     */
    private String attachmentIds;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;
}
