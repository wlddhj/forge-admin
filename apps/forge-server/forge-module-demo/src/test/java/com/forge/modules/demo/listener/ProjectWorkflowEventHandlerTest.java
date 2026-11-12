package com.forge.modules.demo.listener;

import com.aizuda.bpm.engine.core.enums.InstanceEventType;
import com.aizuda.bpm.engine.entity.FlwHisInstance;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.forge.framework.tenant.core.context.TenantContextHolder;
import com.forge.modules.demo.entity.BizProject;
import com.forge.modules.demo.mapper.BizProjectMapper;
import com.forge.modules.workflow.listener.event.WorkflowInstanceEvent;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
    private final com.forge.modules.workflow.framework.event.ReliableEventConsumer consumer =
            new com.forge.modules.workflow.framework.event.ReliableEventConsumer(
                    org.mockito.Mockito.mock(com.forge.modules.workflow.mapper.WfEventAuditMapper.class)) {
                @Override
                protected void scheduleRetry(Runnable retry, long delayMs) {
                    retry.run();
                }
            };
    private final ProjectWorkflowEventHandler handler = new ProjectWorkflowEventHandler(projectMapper, consumer);

    /**
     * LambdaUpdateWrapper 的 set/eq 在构造期即解析实体列缓存，纯 Mockito 环境须先注册 BizProject 的 TableInfo
     */
    @BeforeAll
    static void initMybatisPlusLambdaCache() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), BizProject.class);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    private WorkflowInstanceEvent event(String businessKey, InstanceEventType type, String tenantId) {
        return event(businessKey, type, tenantId, null);
    }

    private WorkflowInstanceEvent event(String businessKey, InstanceEventType type, String tenantId, Integer instanceState) {
        return event(businessKey, type, tenantId, instanceState, 789L);
    }

    private WorkflowInstanceEvent event(String businessKey, InstanceEventType type, String tenantId,
                                        Integer instanceState, Long instanceId) {
        FlwHisInstance instance = new FlwHisInstance();
        instance.setId(instanceId);
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
    @DisplayName("end 终态 → CAS 回写 APPROVED，并回填审批完成时间（实例 endTime）")
    void endWritesApprovedWithApproveTime() {
        when(projectMapper.selectById(10L)).thenReturn(approving());
        when(projectMapper.update(any(LambdaUpdateWrapper.class))).thenReturn(1);

        handler.onInstanceEvent(eventWithEndTime("project:10", InstanceEventType.end, "1",
                new java.util.Date(1761000000000L)));

        LambdaUpdateWrapper<BizProject> wrapper = capturedUpdateWrapper();
        assertTrue(wrapper.getParamNameValuePairs().containsValue(BizProject.STATUS_APPROVED));
        assertTrue(wrapper.getParamNameValuePairs().containsValue(
                LocalDateTime.ofInstant(Instant.ofEpochMilli(1761000000000L), ZoneId.systemDefault())),
                "终态回写应带审批完成时间");
        assertTrue(wrapper.getParamNameValuePairs().containsValue(BizProject.STATUS_APPROVING),
                "CAS 条件更新应携带 approveStatus=APPROVING 前置条件");
    }

    @Test
    @DisplayName("rejectComplete+terminate(5)=发起人取消 → 回写 CANCELED")
    void rejectCompleteWithTerminateStateWritesCanceled() {
        when(projectMapper.selectById(10L)).thenReturn(approving());
        when(projectMapper.update(any(LambdaUpdateWrapper.class))).thenReturn(1);

        handler.onInstanceEvent(event("project:10", InstanceEventType.rejectComplete, "1", 5));

        LambdaUpdateWrapper<BizProject> wrapper = capturedUpdateWrapper();
        assertTrue(wrapper.getParamNameValuePairs().containsValue(BizProject.STATUS_CANCELED));
    }

    @Test
    @DisplayName("rejectComplete+reject(2)=审批驳回 → 回写 REJECTED")
    void rejectCompleteWithRejectStateWritesRejected() {
        when(projectMapper.selectById(10L)).thenReturn(approving());
        when(projectMapper.update(any(LambdaUpdateWrapper.class))).thenReturn(1);

        handler.onInstanceEvent(event("project:10", InstanceEventType.rejectComplete, "1", 2));

        LambdaUpdateWrapper<BizProject> wrapper = capturedUpdateWrapper();
        assertTrue(wrapper.getParamNameValuePairs().containsValue(BizProject.STATUS_REJECTED));
    }

    @Test
    @DisplayName("end 终态 → 回写 APPROVED，CAS WHERE 同时限定 id 与 APPROVING")
    void endWritesApproved() {
        when(projectMapper.selectById(10L)).thenReturn(approving());
        when(projectMapper.update(any(LambdaUpdateWrapper.class))).thenReturn(1);

        handler.onInstanceEvent(event("project:10", InstanceEventType.end, "1"));

        LambdaUpdateWrapper<BizProject> wrapper = capturedUpdateWrapper();
        assertTrue(wrapper.getParamNameValuePairs().containsValue(BizProject.STATUS_APPROVED));
        assertTrue(wrapper.getSqlSegment().contains("id ="), "CAS 应按主键限定更新范围");
        assertTrue(wrapper.getSqlSegment().contains("approve_status ="), "CAS 应限定仅审批中状态可回写");
        assertTrue(wrapper.getSqlSet().contains("approve_status="));
    }

    @Test
    @DisplayName("revokeComplete → 回写 CANCELED；异步线程租户已重建并清理")
    void revokeWritesCanceledAndRestoresTenant() {
        when(projectMapper.selectById(10L)).thenReturn(approving());
        when(projectMapper.update(any(LambdaUpdateWrapper.class))).thenReturn(1);

        handler.onInstanceEvent(event("project:10", InstanceEventType.revokeComplete, "7"));

        capturedUpdateWrapper();
        assertNull(TenantContextHolder.getTenantId(), "处理完成后租户上下文应清理");
    }

    @Test
    @DisplayName("rejectComplete → 回写 REJECTED；重复事件同值幂等跳过")
    void duplicateEventIsIdempotent() {
        BizProject rejected = approving();
        rejected.setApproveStatus(BizProject.STATUS_REJECTED);
        when(projectMapper.selectById(10L)).thenReturn(rejected);

        handler.onInstanceEvent(event("project:10", InstanceEventType.rejectComplete, "1"));

        verify(projectMapper, never()).update(any());
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
        verify(projectMapper, never()).update(any());
        verify(projectMapper, never()).updateById(any(BizProject.class));
    }

    @Test
    @DisplayName("记录已不存在 → 忽略不抛")
    void missingRecordIsIgnored() {
        when(projectMapper.selectById(99L)).thenReturn(null);
        handler.onInstanceEvent(event("project:99", InstanceEventType.end, "1"));
        verify(projectMapper, never()).update(any());
        verify(projectMapper, never()).updateById(any(BizProject.class));
    }

    @Test
    @DisplayName("事件实例ID与绑定实例不匹配（伪造 businessKey）→ 不回写")
    void mismatchedInstanceIdIsRejected() {
        when(projectMapper.selectById(10L)).thenReturn(approving());

        // 立项绑定实例 789，事件来自实例 999（他人实例伪造 businessKey=project:10）
        handler.onInstanceEvent(event("project:10", InstanceEventType.end, "1", null, 999L));

        verify(projectMapper, never()).update(any());
        verify(projectMapper, never()).updateById(any(BizProject.class));
    }

    @Test
    @DisplayName("processInstanceId 为 null（未绑定实例的旧数据）→ 跳过回写不误伤")
    void unboundLegacyRecordIsSkipped() {
        BizProject legacy = approving();
        legacy.setProcessInstanceId(null);
        when(projectMapper.selectById(10L)).thenReturn(legacy);

        handler.onInstanceEvent(event("project:10", InstanceEventType.end, "1"));

        verify(projectMapper, never()).update(any());
        verify(projectMapper, never()).updateById(any(BizProject.class));
    }

    @Test
    @DisplayName("已终态立项（非 APPROVING）→ CAS 0 行受影响，跳过不二次翻转")
    void terminalRecordIsNotFlippedAgain() {
        // 库内已 APPROVED，事件试图回写 REJECTED：CAS 限定 APPROVING → 0 行
        BizProject approved = approving();
        approved.setApproveStatus(BizProject.STATUS_APPROVED);
        when(projectMapper.selectById(10L)).thenReturn(approved);
        when(projectMapper.update(any(LambdaUpdateWrapper.class))).thenReturn(0);

        handler.onInstanceEvent(event("project:10", InstanceEventType.rejectComplete, "1", 2));

        LambdaUpdateWrapper<BizProject> wrapper = capturedUpdateWrapper();
        assertTrue(wrapper.getSqlSegment().contains("approve_status ="),
                "CAS 应限定仅审批中状态可回写（0 行即已终态）");
        assertTrue(wrapper.getParamNameValuePairs().containsValue(BizProject.STATUS_APPROVING));
        assertFalse(wrapper.getParamNameValuePairs().containsValue(BizProject.STATUS_APPROVED));
    }

    /**
     * 捕获回写使用的 CAS 条件更新 wrapper（update(wrapper) 单参形态），
     * 并先触发 WHERE 段生成以固化 eq 条件参数（MyBatis-Plus eq 参数惰性存储）
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private LambdaUpdateWrapper<BizProject> capturedUpdateWrapper() {
        ArgumentCaptor<LambdaUpdateWrapper> captor = ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(projectMapper).update(captor.capture());
        LambdaUpdateWrapper<BizProject> wrapper = captor.getValue();
        assertNotNull(wrapper.getSqlSegment(), "WHERE 段生成失败");
        return wrapper;
    }

    private BizProject approving() {
        BizProject project = new BizProject();
        project.setId(10L);
        project.setApproveStatus(BizProject.STATUS_APPROVING);
        project.setProcessInstanceId(789L);
        return project;
    }
}
