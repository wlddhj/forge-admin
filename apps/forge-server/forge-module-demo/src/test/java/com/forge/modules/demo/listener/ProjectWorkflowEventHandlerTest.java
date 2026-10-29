package com.forge.modules.demo.listener;

import com.aizuda.bpm.engine.core.enums.InstanceEventType;
import com.aizuda.bpm.engine.entity.FlwHisInstance;
import com.forge.framework.tenant.core.context.TenantContextHolder;
import com.forge.modules.demo.entity.BizProject;
import com.forge.modules.demo.mapper.BizProjectMapper;
import com.forge.modules.workflow.listener.event.WorkflowInstanceEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 立项状态回写事件处理器单测
 */
class ProjectWorkflowEventHandlerTest {

    private final BizProjectMapper projectMapper = mock(BizProjectMapper.class);
    private final ProjectWorkflowEventHandler handler = new ProjectWorkflowEventHandler(projectMapper);

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    private WorkflowInstanceEvent event(String businessKey, InstanceEventType type, String tenantId) {
        return event(businessKey, type, tenantId, null);
    }

    private WorkflowInstanceEvent event(String businessKey, InstanceEventType type, String tenantId, Integer instanceState) {
        FlwHisInstance instance = new FlwHisInstance();
        instance.setId(789L);
        instance.setTenantId(tenantId);
        if (instanceState != null) {
            instance.setInstanceState(instanceState);
        }
        return new WorkflowInstanceEvent(this, type, instance, null, null) {
            @Override
            public String getBusinessKey() {
                return businessKey;
            }
        };
    }

    private WorkflowInstanceEvent eventWithEndTime(String businessKey, InstanceEventType type, String tenantId,
                                                   java.util.Date endTime) {
        FlwHisInstance instance = new FlwHisInstance();
        instance.setId(789L);
        instance.setTenantId(tenantId);
        instance.setEndTime(endTime);
        return new WorkflowInstanceEvent(this, type, instance, null, null) {
            @Override
            public String getBusinessKey() {
                return businessKey;
            }
        };
    }

    @Test
    @DisplayName("end 终态 → 回写 APPROVED，并回填审批完成时间（实例 endTime）")
    void endWritesApprovedWithApproveTime() {
        when(projectMapper.selectById(10L)).thenReturn(approving());

        handler.onInstanceEvent(eventWithEndTime("project:10", InstanceEventType.end, "1",
                new java.util.Date(1761000000000L)));

        ArgumentCaptor<BizProject> captor = ArgumentCaptor.forClass(BizProject.class);
        verify(projectMapper).updateById(captor.capture());
        assertEquals(BizProject.STATUS_APPROVED, captor.getValue().getApproveStatus());
        assertNotNull(captor.getValue().getApproveTime(), "终态回写应带审批完成时间");
    }

    @Test
    @DisplayName("rejectComplete+terminate(5)=发起人取消 → 回写 CANCELED")
    void rejectCompleteWithTerminateStateWritesCanceled() {
        when(projectMapper.selectById(10L)).thenReturn(approving());

        handler.onInstanceEvent(event("project:10", InstanceEventType.rejectComplete, "1", 5));

        ArgumentCaptor<BizProject> captor = ArgumentCaptor.forClass(BizProject.class);
        verify(projectMapper).updateById(captor.capture());
        assertEquals(BizProject.STATUS_CANCELED, captor.getValue().getApproveStatus());
    }

    @Test
    @DisplayName("rejectComplete+reject(2)=审批驳回 → 回写 REJECTED")
    void rejectCompleteWithRejectStateWritesRejected() {
        when(projectMapper.selectById(10L)).thenReturn(approving());

        handler.onInstanceEvent(event("project:10", InstanceEventType.rejectComplete, "1", 2));

        ArgumentCaptor<BizProject> captor = ArgumentCaptor.forClass(BizProject.class);
        verify(projectMapper).updateById(captor.capture());
        assertEquals(BizProject.STATUS_REJECTED, captor.getValue().getApproveStatus());
    }

    @Test
    @DisplayName("end 终态 → 回写 APPROVED")
    void endWritesApproved() {
        when(projectMapper.selectById(10L)).thenReturn(approving());

        handler.onInstanceEvent(event("project:10", InstanceEventType.end, "1"));

        ArgumentCaptor<BizProject> captor = ArgumentCaptor.forClass(BizProject.class);
        verify(projectMapper).updateById(captor.capture());
        assertEquals(BizProject.STATUS_APPROVED, captor.getValue().getApproveStatus());
    }

    @Test
    @DisplayName("revokeComplete → 回写 CANCELED；异步线程租户已重建并清理")
    void revokeWritesCanceledAndRestoresTenant() {
        when(projectMapper.selectById(10L)).thenReturn(approving());

        handler.onInstanceEvent(event("project:10", InstanceEventType.revokeComplete, "7"));

        ArgumentCaptor<BizProject> captor = ArgumentCaptor.forClass(BizProject.class);
        verify(projectMapper).updateById(captor.capture());
        assertEquals(BizProject.STATUS_CANCELED, captor.getValue().getApproveStatus());
        assertNull(TenantContextHolder.getTenantId(), "处理完成后租户上下文应清理");
    }

    @Test
    @DisplayName("rejectComplete → 回写 REJECTED；重复事件同值幂等跳过")
    void duplicateEventIsIdempotent() {
        BizProject rejected = approving();
        rejected.setApproveStatus(BizProject.STATUS_REJECTED);
        when(projectMapper.selectById(10L)).thenReturn(rejected);

        handler.onInstanceEvent(event("project:10", InstanceEventType.rejectComplete, "1"));

        verify(projectMapper, never()).updateById(any(BizProject.class));
    }

    @Test
    @DisplayName("非本业务前缀的实例事件 → 直接忽略")
    void ignoresOtherBusinessKeys() {
        handler.onInstanceEvent(event("leave:10", InstanceEventType.end, "1"));
        verify(projectMapper, never()).selectById(any());
    }

    @Test
    @DisplayName("中间态（suspend/forceComplete）→ 不回写")
    void ignoresIntermediateStates() {
        handler.onInstanceEvent(event("project:10", InstanceEventType.suspend, "1"));
        handler.onInstanceEvent(event("project:10", InstanceEventType.forceComplete, "1"));
        verify(projectMapper, never()).updateById(any(BizProject.class));
    }

    @Test
    @DisplayName("记录已不存在 → 忽略不抛")
    void missingRecordIsIgnored() {
        when(projectMapper.selectById(99L)).thenReturn(null);
        handler.onInstanceEvent(event("project:99", InstanceEventType.end, "1"));
        verify(projectMapper, never()).updateById(any(BizProject.class));
    }

    private BizProject approving() {
        BizProject project = new BizProject();
        project.setId(10L);
        project.setApproveStatus(BizProject.STATUS_APPROVING);
        project.setProcessInstanceId(789L);
        return project;
    }
}
