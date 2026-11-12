package com.forge.modules.workflow.framework.instance;

import com.aizuda.bpm.engine.QueryService;
import com.aizuda.bpm.engine.entity.FlwHisTask;
import com.forge.modules.system.entity.SysAttachment;
import com.forge.modules.system.service.SysAttachmentService;
import com.forge.modules.workflow.dto.comment.ApprovalCommentResponse;
import com.forge.modules.workflow.entity.WfApprovalComment;
import com.forge.modules.workflow.mapper.WfApprovalCommentMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 审批意见服务（实例侧）单元测试：意见写入 taskId 回退、列表附件按评论自持 ID 绑定（防串绑他人附件）
 */
class WfApprovalCommentServiceTest {

    private WfApprovalCommentMapper approvalCommentMapper;
    private SysAttachmentService sysAttachmentService;
    private QueryService queryService;
    private WfApprovalCommentService commentService;

    @BeforeEach
    void setUp() {
        approvalCommentMapper = mock(WfApprovalCommentMapper.class);
        sysAttachmentService = mock(SysAttachmentService.class);
        queryService = mock(QueryService.class);
        commentService = new WfApprovalCommentService(approvalCommentMapper, sysAttachmentService, queryService);
    }

    private WfApprovalComment comment(Long id, Long instanceId, Long taskId, String attachmentIds) {
        WfApprovalComment comment = new WfApprovalComment();
        comment.setId(id);
        comment.setProcessInstanceId(instanceId);
        comment.setTaskId(taskId);
        comment.setTaskDefKey("task_" + id);
        comment.setTaskName("任务" + id);
        comment.setUserId(7L);
        comment.setUserName("张三");
        comment.setActionType("approve");
        comment.setCommentText("同意");
        comment.setAttachmentIds(attachmentIds);
        return comment;
    }

    private SysAttachment attachment(Long id) {
        SysAttachment attachment = new SysAttachment();
        attachment.setId(id);
        attachment.setOriginalName("附件" + id + ".pdf");
        attachment.setFileUrl("/files/" + id);
        return attachment;
    }

    @Test
    @DisplayName("保存意见：taskId 为空回退发起任务ID，非空原样保留")
    void saveApprovalCommentTaskIdFallback() {
        FlwHisTask startTask = new FlwHisTask();
        startTask.setId(55L);
        when(queryService.getStartTaskByInstanceId(100L)).thenReturn(startTask);

        // 实例级动作（taskId=null）：回退挂到发起任务
        commentService.saveApprovalComment(100L, null, "cancel", "取消流程", 7L, "张三", "cancel", "用户主动取消流程");
        ArgumentCaptor<WfApprovalComment> captor = ArgumentCaptor.forClass(WfApprovalComment.class);
        verify(approvalCommentMapper).insert(captor.capture());
        assertEquals(55L, captor.getValue().getTaskId());
        assertEquals("cancel", captor.getValue().getActionType());
        assertEquals("用户主动取消流程", captor.getValue().getCommentText());

        // 任务级动作：taskId 原样保留，不再查询发起任务
        commentService.saveApprovalComment(100L, 66L, "approve", "审批", 7L, "张三", "approve", "同意");
        ArgumentCaptor<WfApprovalComment> captor2 = ArgumentCaptor.forClass(WfApprovalComment.class);
        verify(approvalCommentMapper, times(2)).insert(captor2.capture());
        assertEquals(66L, captor2.getValue().getTaskId());
        verify(queryService, times(1)).getStartTaskByInstanceId(any());
    }

    @Test
    @DisplayName("意见列表：各评论只绑定自己声明的附件（防夺他人附件），按 createTime 升序查询")
    void getApprovalCommentsBindsOwnAttachmentsOnly() {
        WfApprovalComment c1 = comment(1L, 100L, 11L, "101,102");
        WfApprovalComment c2 = comment(2L, 100L, 12L, "201");
        when(approvalCommentMapper.selectList(any())).thenReturn(List.of(c1, c2));
        when(sysAttachmentService.listByIds(any())).thenReturn(
                List.of(attachment(101L), attachment(102L), attachment(201L)));

        List<ApprovalCommentResponse> responses = commentService.getApprovalComments(100L);

        assertEquals(2, responses.size());
        // 评论1 只绑自己的 101/102
        assertEquals(List.of(101L, 102L),
                responses.get(0).getAttachments().stream().map(b -> b.getId()).toList());
        // 评论2 只绑自己的 201，不夺评论1的附件
        assertEquals(List.of(201L),
                responses.get(1).getAttachments().stream().map(b -> b.getId()).toList());
        // 基础字段映射
        assertEquals("张三", responses.get(0).getUserName());
        assertEquals("同意", responses.get(0).getCommentText());
    }

    @Test
    @DisplayName("评论无附件与附件ID非法/缺失时不绑定明细，批量查询去重一次完成")
    void getApprovalCommentsSkipsBlankAndMissingAttachments() {
        WfApprovalComment noAttachment = comment(1L, 100L, 11L, null);
        WfApprovalComment dirtyIds = comment(2L, 100L, 12L, "301,abc");
        when(approvalCommentMapper.selectList(any())).thenReturn(List.of(noAttachment, dirtyIds));
        when(sysAttachmentService.listByIds(any())).thenReturn(List.of()); // 附件库已不存在

        List<ApprovalCommentResponse> responses = commentService.getApprovalComments(100L);

        assertNull(responses.get(0).getAttachments());
        assertTrue(responses.get(1).getAttachments().isEmpty());
        // 单次批量查询（去重后仅 301），无 N+1
        verify(sysAttachmentService, times(1)).listByIds(any());
    }
}
