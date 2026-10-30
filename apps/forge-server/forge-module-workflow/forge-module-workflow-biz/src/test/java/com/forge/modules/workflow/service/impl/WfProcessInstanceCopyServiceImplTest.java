package com.forge.modules.workflow.service.impl;

import com.aizuda.bpm.engine.FlowLongEngine;
import com.aizuda.bpm.engine.QueryService;
import com.aizuda.bpm.engine.core.enums.ActorType;
import com.aizuda.bpm.engine.core.enums.InstanceEventType;
import com.aizuda.bpm.engine.core.enums.TaskType;
import com.aizuda.bpm.engine.entity.*;
import com.aizuda.bpm.mybatisplus.mapper.FlwHisTaskActorMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.forge.common.exception.BusinessException;
import com.forge.common.utils.UserContext;
import com.forge.framework.security.utils.SecurityHelper;
import com.forge.modules.workflow.entity.WfProcessExt;
import com.forge.modules.workflow.framework.copy.AutoCopyUserResolver;
import com.forge.modules.workflow.identity.FlowLongIdentityService;
import com.forge.modules.workflow.listener.event.WfAutoCopyEventHandler;
import com.forge.modules.workflow.listener.event.WorkflowInstanceEvent;
import com.forge.modules.workflow.mapper.WfProcessExtMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 抄送链路单元测试
 *
 * 覆盖：终态事件触发自动抄送、同人去重、手动抄送权限/锚点/重复拦截
 */
class WfProcessInstanceCopyServiceImplTest {

    private static final Long INSTANCE_ID = 100L;
    private static final Long PROCESS_ID = 10L;

    private FlowLongEngine flowLongEngine;
    private QueryService queryService;
    private WfProcessExtMapper processExtMapper;
    private AutoCopyUserResolver autoCopyUserResolver;
    private FlowLongIdentityService identityService;
    private FlwHisTaskActorMapper hisTaskActorMapper;
    private WfProcessInstanceCopyServiceImpl service;

    @BeforeEach
    void setUp() {
        flowLongEngine = mock(FlowLongEngine.class);
        queryService = mock(QueryService.class);
        processExtMapper = mock(WfProcessExtMapper.class);
        autoCopyUserResolver = mock(AutoCopyUserResolver.class);
        identityService = mock(FlowLongIdentityService.class);
        hisTaskActorMapper = mock(FlwHisTaskActorMapper.class);
        when(flowLongEngine.queryService()).thenReturn(queryService);
        service = new WfProcessInstanceCopyServiceImpl(flowLongEngine, queryService,
                processExtMapper, autoCopyUserResolver, identityService, hisTaskActorMapper,
                new ObjectMapper());
    }

    private WfProcessExt extWithStrategy(Integer strategy, String param) {
        WfProcessExt ext = new WfProcessExt();
        ext.setProcessId(PROCESS_ID);
        ext.setAutoCopyStrategy(strategy);
        ext.setAutoCopyParam(param);
        return ext;
    }

    @SuppressWarnings("unchecked")
    private org.mockito.stubbing.Answer<Object> selectExtAnswer(WfProcessExt ext) {
        return inv -> ext;
    }

    @Test
    @DisplayName("终态事件处理：未配置策略时不产生抄送")
    void autoCopy_shouldSkipWithoutStrategy() {
        when(processExtMapper.selectOne(any())).thenReturn(null);
        FlwHisInstance hisInstance = new FlwHisInstance();
        hisInstance.setId(INSTANCE_ID);
        hisInstance.setProcessId(PROCESS_ID);
        when(queryService.getInstance(INSTANCE_ID)).thenReturn(null);
        when(queryService.getHistInstance(INSTANCE_ID)).thenReturn(hisInstance);

        service.autoCopyOnProcessEnd(String.valueOf(INSTANCE_ID), String.valueOf(PROCESS_ID), "流程结束自动抄送（end）");

        verify(flowLongEngine, never()).createCcTask(any(), anyList(), any());
    }

    @Test
    @DisplayName("自动抄送：策略解析用户并经发起任务锚点生成抄送")
    void autoCopy_shouldCreateCcForResolvedUsers() {
        when(processExtMapper.selectOne(any())).thenAnswer(selectExtAnswer(extWithStrategy(30, "8,9")));
        FlwHisInstance hisInstance = new FlwHisInstance();
        hisInstance.setId(INSTANCE_ID);
        hisInstance.setProcessId(PROCESS_ID);
        hisInstance.setCreateId("1");
        when(queryService.getInstance(INSTANCE_ID)).thenReturn(null);
        when(queryService.getHistInstance(INSTANCE_ID)).thenReturn(hisInstance);

        FlwHisTask startTask = new FlwHisTask();
        startTask.setId(1000L);
        startTask.setInstanceId(INSTANCE_ID);
        startTask.setTaskKey("start_1");
        when(queryService.getStartTaskByInstanceId(INSTANCE_ID)).thenReturn(startTask);

        when(autoCopyUserResolver.resolveUsers(eq(30), eq("8,9"), eq(1L), anyMap()))
                .thenReturn(Set.of(8L, 9L));
        when(identityService.getUserName(8L)).thenReturn("用户8");
        when(identityService.getUserName(9L)).thenReturn("用户9");
        when(identityService.getUserName(1L)).thenReturn("发起人");

        service.autoCopyOnProcessEnd(String.valueOf(INSTANCE_ID), String.valueOf(PROCESS_ID), "流程结束自动抄送（end）");

        ArgumentCaptor<List<FlwTaskActor>> actorsCaptor = ArgumentCaptor.forClass(List.class);
        verify(flowLongEngine).createCcTask(eq(startTask), actorsCaptor.capture(), any());
        assertEquals(2, actorsCaptor.getValue().size());
        assertEquals(Set.of("8", "9"), actorsCaptor.getValue().stream()
                .map(FlwTaskActor::getActorId).collect(java.util.stream.Collectors.toSet()));
        assertTrue(actorsCaptor.getValue().stream()
                .allMatch(a -> ActorType.user.eq(a.getActorType())));
    }

    @Test
    @DisplayName("自动抄送：同实例已抄送用户被去重")
    void autoCopy_shouldDedupExistingCcActors() {
        when(processExtMapper.selectOne(any())).thenAnswer(selectExtAnswer(extWithStrategy(30, "8")));
        FlwHisInstance hisInstance = new FlwHisInstance();
        hisInstance.setId(INSTANCE_ID);
        hisInstance.setProcessId(PROCESS_ID);
        hisInstance.setCreateId("1");
        when(queryService.getInstance(INSTANCE_ID)).thenReturn(null);
        when(queryService.getHistInstance(INSTANCE_ID)).thenReturn(hisInstance);
        FlwHisTask startTask = new FlwHisTask();
        startTask.setId(1000L);
        when(queryService.getStartTaskByInstanceId(INSTANCE_ID)).thenReturn(startTask);

        FlwHisTaskActor existing = new FlwHisTaskActor();
        existing.setInstanceId(INSTANCE_ID);
        existing.setTaskId(1000L);
        existing.setActorId("8");
        existing.setWeight(6);
        when(hisTaskActorMapper.selectList(any())).thenReturn(new java.util.ArrayList<>(List.of(existing)));

        when(autoCopyUserResolver.resolveUsers(any(), any(), any(), anyMap())).thenReturn(Set.of(8L));

        service.autoCopyOnProcessEnd(String.valueOf(INSTANCE_ID), String.valueOf(PROCESS_ID), "流程结束自动抄送（end）");

        verify(flowLongEngine, never()).createCcTask(any(), anyList(), any());
    }

    @Test
    @DisplayName("手动抄送：非发起人且非管理员被拒绝")
    void manualCopy_shouldRejectNonInitiator() {
        FlwInstance instance = new FlwInstance();
        instance.setId(INSTANCE_ID);
        instance.setCreateId("1");
        when(queryService.getInstance(INSTANCE_ID)).thenReturn(instance);

        try (var securityMock = mockStatic(SecurityHelper.class);
             var userContextMock = mockStatic(UserContext.class)) {
            securityMock.when(SecurityHelper::getCurrentUserId).thenReturn(7L);
            userContextMock.when(() -> UserContext.get()).thenReturn(null);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.manualCopy(String.valueOf(INSTANCE_ID), List.of(8L), "请知悉"));
            assertEquals(403, ex.getCode());
        }
    }

    @Test
    @DisplayName("手动抄送：流程已结束被拒绝")
    void manualCopy_shouldRejectFinishedInstance() {
        when(queryService.getInstance(INSTANCE_ID)).thenReturn(null);

        try (var securityMock = mockStatic(SecurityHelper.class)) {
            securityMock.when(SecurityHelper::getCurrentUserId).thenReturn(1L);
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.manualCopy(String.valueOf(INSTANCE_ID), List.of(8L), "请知悉"));
            assertEquals(400, ex.getCode());
        }
    }

    @Test
    @DisplayName("手动抄送：发起人可抄送且锚点取当前活动审批任务")
    void manualCopy_shouldUseActiveApprovalAnchor() {
        FlwInstance instance = new FlwInstance();
        instance.setId(INSTANCE_ID);
        instance.setCreateId("1");
        when(queryService.getInstance(INSTANCE_ID)).thenReturn(instance);

        FlwTask activeApproval = new FlwTask();
        activeApproval.setId(2000L);
        activeApproval.setInstanceId(INSTANCE_ID);
        activeApproval.setTaskKey("node_2");
        activeApproval.setTaskName("二级审批");
        activeApproval.setTaskType(TaskType.approval.getValue());
        when(queryService.getTasksByInstanceId(INSTANCE_ID)).thenReturn(List.of(activeApproval));

        when(identityService.getUserName(8L)).thenReturn("用户8");
        when(identityService.getUserName(1L)).thenReturn("发起人");

        try (var securityMock = mockStatic(SecurityHelper.class)) {
            securityMock.when(SecurityHelper::getCurrentUserId).thenReturn(1L);
            service.manualCopy(String.valueOf(INSTANCE_ID), List.of(8L), "请知悉");
        }

        verify(flowLongEngine).createCcTask(eq(activeApproval), anyList(), any());
    }

    @Test
    @DisplayName("手动抄送：所选用户均已抄送时返回业务错误")
    void manualCopy_shouldRejectWhenAllDuplicated() {
        FlwInstance instance = new FlwInstance();
        instance.setId(INSTANCE_ID);
        instance.setCreateId("1");
        when(queryService.getInstance(INSTANCE_ID)).thenReturn(instance);
        FlwTask activeApproval = new FlwTask();
        activeApproval.setId(2000L);
        activeApproval.setTaskType(TaskType.approval.getValue());
        when(queryService.getTasksByInstanceId(INSTANCE_ID)).thenReturn(List.of(activeApproval));

        FlwHisTaskActor existing = new FlwHisTaskActor();
        existing.setActorId("8");
        existing.setWeight(6);
        when(hisTaskActorMapper.selectList(any())).thenReturn(new java.util.ArrayList<>(List.of(existing)));

        try (var securityMock = mockStatic(SecurityHelper.class)) {
            securityMock.when(SecurityHelper::getCurrentUserId).thenReturn(1L);
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.manualCopy(String.valueOf(INSTANCE_ID), List.of(8L), "请知悉"));
            assertEquals(400, ex.getCode());
        }
    }

    @Test
    @DisplayName("终态事件处理器：仅终态事件触发抄送，start事件跳过")
    void eventHandler_shouldOnlyReactToTerminalEvents() {
        WfProcessInstanceCopyServiceForTest copyService = new WfProcessInstanceCopyServiceForTest();
        // consumer 直跑无重试：该用例关注事件过滤，可靠性由 ReliableEventConsumerTest 覆盖
        com.forge.modules.workflow.framework.event.ReliableEventConsumer noopConsumer =
                new com.forge.modules.workflow.framework.event.ReliableEventConsumer(
                        org.mockito.Mockito.mock(com.forge.modules.workflow.mapper.WfEventAuditMapper.class)) {
                    @Override
                    protected void scheduleRetry(Runnable retry, long delayMs) {
                        retry.run();
                    }
                };
        WfAutoCopyEventHandler handler = new WfAutoCopyEventHandler(copyService, noopConsumer);

        FlwHisInstance hisInstance = new FlwHisInstance();
        hisInstance.setId(INSTANCE_ID);
        hisInstance.setProcessId(PROCESS_ID);
        WorkflowInstanceEvent endEvent = new WorkflowInstanceEvent(
                this, InstanceEventType.end, hisInstance, null, null);
        handler.onInstanceEvent(endEvent);
        assertEquals(1, copyService.called);

        WorkflowInstanceEvent startEvent = new WorkflowInstanceEvent(
                this, InstanceEventType.start, hisInstance, null, null);
        handler.onInstanceEvent(startEvent);
        assertEquals(1, copyService.called, "start事件不应触发自动抄送");
    }

    /** 计数用的测试替身 */
    private static class WfProcessInstanceCopyServiceForTest implements com.forge.modules.workflow.service.WfProcessInstanceCopyService {
        int called;

        @Override
        public void autoCopyOnProcessEnd(String processInstanceId, String processDefinitionId, String reason) {
            called++;
        }

        @Override
        public void manualCopy(String processInstanceId, List<Long> copyUserIds, String reason) {
        }
    }
}
