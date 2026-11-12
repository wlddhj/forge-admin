package com.forge.modules.workflow.framework.instance;

import cn.hutool.core.util.StrUtil;
import com.aizuda.bpm.engine.QueryService;
import com.aizuda.bpm.engine.entity.FlwHisTask;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.forge.modules.system.entity.SysAttachment;
import com.forge.modules.system.service.SysAttachmentService;
import com.forge.modules.workflow.dto.comment.ApprovalCommentResponse;
import com.forge.modules.workflow.dto.comment.AttachmentBrief;
import com.forge.modules.workflow.entity.WfApprovalComment;
import com.forge.modules.workflow.mapper.WfApprovalCommentMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 审批意见服务（实例侧：动作意见写入与意见列表查询，自 WfProcessInstanceServiceImpl 平移；
 * 任务侧写入后续合并至本服务）
 *
 * @author forge-admin
 */
@Component
@RequiredArgsConstructor
public class WfApprovalCommentService {

    private final WfApprovalCommentMapper approvalCommentMapper;
    private final SysAttachmentService sysAttachmentService;
    private final QueryService queryService;

    /**
     * 获取流程实例的审批意见列表（按创建时间升序，附件明细批量填充）
     */
    public List<ApprovalCommentResponse> getApprovalComments(Long processInstanceId) {
        LambdaQueryWrapper<WfApprovalComment> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(WfApprovalComment::getProcessInstanceId, processInstanceId)
                .orderByAsc(WfApprovalComment::getCreateTime);

        List<WfApprovalComment> comments = approvalCommentMapper.selectList(wrapper);
        List<ApprovalCommentResponse> responses = comments.stream()
                .map(this::convertToCommentResponse)
                .collect(Collectors.toList());
        fillCommentAttachments(comments, responses);
        return responses;
    }

    /**
     * 保存审批意见（实例级动作 taskId 传 null 时回退挂到发起任务，task_id 列非空）
     */
    public void saveApprovalComment(Long processInstanceId, Long taskId,
                                      String taskDefKey, String taskName,
                                      Long userId, String userName,
                                      String actionType, String commentText) {
        Long effectiveTaskId = taskId;
        if (effectiveTaskId == null) {
            FlwHisTask startTask = queryService.getStartTaskByInstanceId(processInstanceId);
            effectiveTaskId = startTask != null ? startTask.getId() : 0L;
        }
        WfApprovalComment comment = new WfApprovalComment();
        comment.setProcessInstanceId(processInstanceId);
        comment.setTaskId(effectiveTaskId);
        comment.setTaskDefKey(taskDefKey);
        comment.setTaskName(taskName);
        comment.setUserId(userId);
        comment.setUserName(userName);
        comment.setActionType(actionType);
        comment.setCommentText(commentText);
        comment.setCreateTime(LocalDateTime.now());
        approvalCommentMapper.insert(comment);
    }

    /**
     * 转换审批意见为响应对象
     */
    private ApprovalCommentResponse convertToCommentResponse(WfApprovalComment comment) {
        ApprovalCommentResponse response = new ApprovalCommentResponse();
        response.setId(comment.getId());
        response.setProcessInstanceId(comment.getProcessInstanceId());
        response.setTaskId(comment.getTaskId());
        response.setTaskDefKey(comment.getTaskDefKey());
        response.setTaskName(comment.getTaskName());
        response.setUserId(comment.getUserId());
        response.setUserName(comment.getUserName());
        response.setActionType(comment.getActionType());
        response.setCommentText(comment.getCommentText());
        response.setAttachmentIds(comment.getAttachmentIds());
        if (comment.getCreateTime() != null) {
            response.setCreateTime(comment.getCreateTime());
        }
        return response;
    }

    /**
     * 按评论的 attachment_ids 批量填充附件明细（一次查询，避免逐条 N+1）
     */
    private void fillCommentAttachments(List<WfApprovalComment> comments, List<ApprovalCommentResponse> responses) {
        List<Long> allAttachmentIds = comments.stream()
                .map(WfApprovalComment::getAttachmentIds)
                .filter(StrUtil::isNotBlank)
                .flatMap(s -> Arrays.stream(s.split(",")))
                .filter(s -> s.matches("\\d+"))
                .map(Long::parseLong)
                .distinct()
                .collect(Collectors.toList());
        if (allAttachmentIds.isEmpty()) {
            return;
        }

        Map<Long, SysAttachment> attachmentMap = sysAttachmentService.listByIds(allAttachmentIds).stream()
                .collect(Collectors.toMap(SysAttachment::getId, a -> a));
        for (int i = 0; i < comments.size(); i++) {
            String idsStr = comments.get(i).getAttachmentIds();
            if (StrUtil.isBlank(idsStr)) {
                continue;
            }
            List<AttachmentBrief> briefs = Arrays.stream(idsStr.split(","))
                    .filter(s -> s.matches("\\d+"))
                    .map(Long::parseLong)
                    .map(attachmentMap::get)
                    .filter(Objects::nonNull)
                    .map(attachment -> {
                        AttachmentBrief brief = new AttachmentBrief();
                        brief.setId(attachment.getId());
                        brief.setOriginalName(attachment.getOriginalName());
                        brief.setFileUrl(attachment.getFileUrl());
                        return brief;
                    })
                    .collect(Collectors.toList());
            responses.get(i).setAttachments(briefs);
        }
    }
}
