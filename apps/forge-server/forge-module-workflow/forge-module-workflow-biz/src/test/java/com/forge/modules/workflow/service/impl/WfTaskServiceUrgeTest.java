package com.forge.modules.workflow.service.impl;

import com.aizuda.bpm.engine.FlowLongEngine;
import com.aizuda.bpm.engine.ProcessService;
import com.aizuda.bpm.engine.QueryService;
import com.aizuda.bpm.engine.RuntimeService;
import com.aizuda.bpm.engine.TaskService;
import com.aizuda.bpm.engine.core.FlowLongContext;
import com.aizuda.bpm.engine.core.enums.ActorType;
import com.aizuda.bpm.engine.core.enums.TaskType;
import com.aizuda.bpm.engine.entity.FlwInstance;
import com.aizuda.bpm.engine.entity.FlwProcess;
import com.aizuda.bpm.engine.entity.FlwTask;
import com.aizuda.bpm.engine.entity.FlwTaskActor;
import com.aizuda.bpm.mybatisplus.mapper.*;
import com.aizuda.bpm.spring.adaptive.FlowJacksonHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.forge.common.exception.BusinessException;
import com.forge.common.utils.UserContext;
import com.forge.framework.security.utils.SecurityHelper;
import com.forge.framework.web.websocket.NotificationService;
import com.forge.modules.system.service.SysAttachmentService;
import com.forge.modules.workflow.config.FlowLongSchedulerProperties;
import com.forge.modules.workflow.entity.WfApprovalComment;
import com.forge.modules.workflow.identity.FlowLongIdentityService;
import com.forge.modules.workflow.mapper.WfApprovalCommentMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 手动催办单元测试
 *
 * 覆盖：权限校验、无审批任务、节流窗口、通知与留痕
 */
class WfTaskServiceUrgeTest {

    private static final Long INSTANCE_ID = 100L;

    private TaskService taskService;
    private ProcessService processService;
    private QueryService queryService;
    private FlowLongIdentityService identityService;
    private WfApprovalCommentMapper approvalCommentMapper;
    private NotificationService notificationService;
    private FlowLongSchedulerProperties schedulerProperties;
    private WfTaskServiceImpl service;

    @BeforeEach
    void setUp() {
        // FlwTask.variableMap 依赖引擎上下文的 JSON handler，单测环境手动装配
        FlowLongContext.setFlowJsonHandler(new FlowJacksonHandler());
        taskService = mock(TaskService.class);
        processService = mock(ProcessService.class);
        queryService = mock(QueryService.class);
        identityService = mock(FlowLongIdentityService.class);
        approvalCommentMapper = mock(WfApprovalCommentMapper.class);
        notificationService = mock(NotificationService.class);
        schedulerProperties = new FlowLongSchedulerProperties();
        schedulerProperties.setUrgeIntervalMinutes(30);

        service = new WfTaskServiceImpl(taskService, processService, mock(RuntimeService.class),
                queryService, identityService, approvalCommentMapper,
                mock(FlwTaskMapper.class), mock(FlwHisTaskMapper.class),
                mock(FlwTaskActorMapper.class), mock(FlwHisTaskActorMapper.class),
                mock(FlwInstanceMapper.class), mock(FlwHisInstanceMapper.class),
                mock(FlowLongEngine.class), mock(SysAttachmentService.class),
                notificationService, schedulerProperties, new ObjectMapper());
    }

    private FlwInstance runningInstance() {
        FlwInstance instance = new FlwInstance();
        instance.setId(INSTANCE_ID);
        instance.setCreateId("1");
        instance.setProcessId(10L);
        return instance;
    }

    private FlwTask approvalTask(String variableJson) {
        FlwTask task = new FlwTask();
        task.setId(1000L);
        task.setInstanceId(INSTANCE_ID);
        task.setTaskKey("node_1");
        task.setTaskName("部门审批");
        task.setTaskType(TaskType.approval.getValue());
        task.setCreateTime(new java.util.Date(System.currentTimeMillis() - 3600_000));
        if (variableJson != null) {
            task.setVariable(variableJson);
        }
        return task;
    }

    private void stubApprovalTasks(FlwTask... tasks) {
        FlwInstance instance = runningInstance();
        when(queryService.getInstance(INSTANCE_ID)).thenReturn(instance);
        FlwProcess process = new FlwProcess();
        process.setId(10L);
        process.setProcessName("请假流程");
        when(processService.getProcessById(10L)).thenReturn(process);
        when(queryService.getTasksByInstanceId(INSTANCE_ID)).thenReturn(List.of(tasks));
    }

    @Test
    @DisplayName("非发起人且非管理员被拒绝催办")
    void urge_shouldRejectNonInitiator() {
        stubApprovalTasks(approvalTask(null));
        when(identityService.getUserName(7L)).thenReturn("路人");

        try (var securityMock = mockStatic(SecurityHelper.class);
             var userContextMock = mockStatic(UserContext.class)) {
            securityMock.when(SecurityHelper::getCurrentUserId).thenReturn(7L);
            userContextMock.when(() -> UserContext.get()).thenReturn(null);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.urgeInstance(String.valueOf(INSTANCE_ID), null));
            assertEquals(403, ex.getCode());
        }
    }

    @Test
    @DisplayName("无活动审批任务时提示无需催办")
    void urge_shouldRejectWhenNoApprovalTask() {
        FlwInstance instance = runningInstance();
        when(queryService.getInstance(INSTANCE_ID)).thenReturn(instance);
        // 仅有抄送类任务
        FlwTask ccTask = new FlwTask();
        ccTask.setTaskType(TaskType.cc.getValue());
        when(queryService.getTasksByInstanceId(INSTANCE_ID)).thenReturn(List.of(ccTask));

        try (var securityMock = mockStatic(SecurityHelper.class)) {
            securityMock.when(SecurityHelper::getCurrentUserId).thenReturn(1L);
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.urgeInstance(String.valueOf(INSTANCE_ID), null));
            assertEquals(400, ex.getCode());
            assertTrue(ex.getMessage().contains("无待审批任务"));
        }
    }

    @Test
    @DisplayName("节流窗口内重复催办被拒绝且不发送通知")
    void urge_shouldThrottleWithinInterval() {
        // 10 分钟前催办过，窗口 30 分钟
        long lastUrge = System.currentTimeMillis() - 10 * 60_000L;
        stubApprovalTasks(approvalTask("{\"lastUrgeTime\":" + lastUrge + "}"));
        when(identityService.getUserName(1L)).thenReturn("发起人");

        try (var securityMock = mockStatic(SecurityHelper.class);
             var userContextMock = mockStatic(UserContext.class)) {
            securityMock.when(SecurityHelper::getCurrentUserId).thenReturn(1L);
            userContextMock.when(() -> UserContext.get()).thenReturn(null);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.urgeInstance(String.valueOf(INSTANCE_ID), "请尽快处理"));
            assertEquals(400, ex.getCode());
            assertTrue(ex.getMessage().contains("催办过于频繁"));
        }
        verify(notificationService, never()).sendToUser(anyLong(), any());
        verify(approvalCommentMapper, never()).insert(any(WfApprovalComment.class));
    }

    @Test
    @DisplayName("发起人催办：通知用户参与者并留痕审批意见")
    void urge_shouldNotifyActorsAndSaveComment() {
        stubApprovalTasks(approvalTask(null));
        when(identityService.getUserName(1L)).thenReturn("发起人");

        FlwTaskActor actor = new FlwTaskActor();
        actor.setTaskId(1000L);
        actor.setActorId("2");
        actor.setActorName("审批人甲");
        actor.setActorType(ActorType.user.getValue());
        FlwTaskActor roleActor = new FlwTaskActor();
        roleActor.setTaskId(1000L);
        roleActor.setActorId("30");
        roleActor.setActorName("经理角色");
        roleActor.setActorType(ActorType.role.getValue());
        when(queryService.getTaskActorsByTaskId(1000L)).thenReturn(List.of(actor, roleActor));

        try (var securityMock = mockStatic(SecurityHelper.class);
             var userContextMock = mockStatic(UserContext.class)) {
            securityMock.when(SecurityHelper::getCurrentUserId).thenReturn(1L);
            userContextMock.when(() -> UserContext.get()).thenReturn(null);

            service.urgeInstance(String.valueOf(INSTANCE_ID), "今天必须处理");
        }

        // 仅用户参与者收到通知（角色池不直接推送）
        verify(notificationService).sendToUser(eq(2L), argThat(message ->
                message != null && message.getTitle().contains("催办")
                        && message.getContent().contains("今天必须处理")));
        verify(notificationService, times(1)).sendToUser(anyLong(), any());

        ArgumentCaptor<WfApprovalComment> commentCaptor = ArgumentCaptor.forClass(WfApprovalComment.class);
        verify(approvalCommentMapper).insert(commentCaptor.capture());
        assertEquals("urge", commentCaptor.getValue().getActionType());
        assertTrue(commentCaptor.getValue().getCommentText().contains("今天必须处理"));

        // 节流标记已写入任务变量
        verify(taskService).updateTaskById(argThat(update ->
                        update != null && update.getId().equals(1000L)
                                && update.getVariable() != null && update.getVariable().contains("lastUrgeTime")),
                any());
    }
}
