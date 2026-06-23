package com.forge.modules.workflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 审批意见实体
 *
 * @author forge-admin
 */
@Data
@TableName("wf_approval_comment")
public class WfApprovalComment {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 流程实例ID
     */
    private Long processInstanceId;

    /**
     * 任务ID
     */
    private Long taskId;

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
     * 操作类型：submit-提交, approve-通过, reject-驳回, delegate-委派, transfer-转办, return-退回, cancel-取消
     */
    private String actionType;

    /**
     * 审批意见
     */
    private String commentText;

    /**
     * 附件ID列表（逗号分隔）
     */
    private String attachmentIds;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
