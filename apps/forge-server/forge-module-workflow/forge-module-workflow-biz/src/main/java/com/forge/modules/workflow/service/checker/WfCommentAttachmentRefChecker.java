package com.forge.modules.workflow.service.checker;

import com.forge.modules.system.constant.AttachmentBizTypes;
import com.forge.modules.system.entity.SysAttachment;
import com.forge.modules.system.service.spi.AttachmentRefChecker;
import com.forge.modules.workflow.entity.WfApprovalComment;
import com.forge.modules.workflow.mapper.WfApprovalCommentMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 审批评论附件引用校验：附件行 bizId 指向的评论仍存活即视为被引用
 */
@Component
@RequiredArgsConstructor
public class WfCommentAttachmentRefChecker implements AttachmentRefChecker {

    private final WfApprovalCommentMapper wfApprovalCommentMapper;

    @Override
    public String bizType() {
        return AttachmentBizTypes.WF_COMMENT;
    }

    @Override
    public String checkReferenced(SysAttachment attachment) {
        if (attachment.getBizId() == null) {
            return null;
        }
        WfApprovalComment comment = wfApprovalCommentMapper.selectById(attachment.getBizId());
        return comment != null ? "审批评论（#" + comment.getId() + "）" : null;
    }
}
