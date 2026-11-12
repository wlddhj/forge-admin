package com.forge.modules.workflow.service.impl;

import com.forge.modules.workflow.framework.task.WfUrgeService;

import com.forge.modules.workflow.framework.task.TaskResponseAssembler;

import com.forge.modules.workflow.framework.task.WfTaskQueryService;

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
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import tools.jackson.databind.ObjectMapper;
import com.forge.common.exception.BusinessException;
import com.forge.common.utils.UserContext;
import com.forge.framework.security.utils.SecurityHelper;
import com.forge.framework.web.websocket.NotificationService;
import com.forge.modules.system.service.SysAttachmentService;
import com.forge.modules.workflow.config.FlowLongSchedulerProperties;
import com.forge.modules.workflow.entity.WfApprovalComment;
import com.forge.modules.workflow.identity.FlowLongIdentityService;
import com.forge.modules.workflow.mapper.WfApprovalCommentMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
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
 * 覆盖：权限校验、无审批任务、行锁读（FOR UPDATE）、节流窗口、通知与留痕
 */
class WfTaskServiceUrgeTest {

    private static final Long INSTANCE_ID = 100L;

    private TaskService taskService;
    private ProcessService processService;
    private QueryService queryService;
    private FlowLongIdentityService identityService;
    private WfApprovalCommentMapper approvalCommentMapper;
    private org.springframework.context.ApplicationEventPublisher eventPublisher;
    private FlowLongSchedulerProperties schedulerProperties;
    private FlwTaskMapper taskMapper;
    private WfUrgeService urgeService;
    private WfTaskServiceImpl service;

    @BeforeAll
    static void initTableInfo() {
        // MP 3.5.18：纯单测下 LambdaQueryWrapper 渲染（getSqlSegment）需要实体 TableInfo 缓存
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, FlwTask.class);
    }

    @BeforeEach
    void setUp() {
        // FlwTask.variableMap 依赖引擎上下文的 JSON handler，单测环境手动装配
        FlowLongContext.setFlowJsonHandler(new FlowJacksonHandler());
        taskService = mock(TaskService.class);
        processService = mock(ProcessService.class);
        queryService = mock(QueryService.class);
        identityService = mock(FlowLongIdentityService.class);
        approvalCommentMapper = mock(WfApprovalCommentMapper.class);
        eventPublisher = mock(org.springframework.context.ApplicationEventPublisher.class);
        schedulerProperties = new FlowLongSchedulerProperties();
        schedulerProperties.setUrgeIntervalMinutes(30);
        taskMapper = mock(FlwTaskMapper.class);

        // 催办已收敛至 WfUrgeService：同一组 mock 装配真实催办服务，主类经其委托
        // （断言对象 taskService/taskMapper/eventPublisher/approvalCommentMapper 均为同一实例）
        urgeService = new WfUrgeService(taskService, processService, queryService, identityService,
                taskMapper, approvalCommentMapper, eventPublisher, schedulerProperties, new ObjectMapper());

        service = new WfTaskServiceImpl(taskService, mock(RuntimeService.class),
                queryService, identityService, approvalCommentMapper,
                mock(FlwHisTaskMapper.class), mock(FlwHisTaskActorMapper.class),
                mock(FlwInstanceMapper.class), mock(FlowLongEngine.class), mock(SysAttachmentService.class),
                new ObjectMapper(), permissiveAccessStrategy(),
                mock(WfTaskQueryService.class), mock(TaskResponseAssembler.class), urgeService);
    }

    /**
     * 行锁读桩：对将催办任务先 FOR UPDATE 当前读，按调用顺序返回锁内任务行
     * （与列表快照独立——锁内行才是节流判定依据）
     */
    private void stubLockedTasks(FlwTask... lockedTasks) {
        var stubbing = when(taskMapper.selectOne(any()));
        for (FlwTask locked : lockedTasks) {
            stubbing = stubbing.thenReturn(locked);
        }
    }

    /** 归属校验放行桩：本测试类聚焦催办行为，参与者判定恒通过 */
    private com.forge.modules.workflow.framework.actor.ForgeTaskAccessStrategy permissiveAccessStrategy() {
        com.forge.modules.workflow.framework.actor.ForgeTaskAccessStrategy strategy =
                mock(com.forge.modules.workflow.framework.actor.ForgeTaskAccessStrategy.class);
        when(strategy.isAllowed(anyString(), any())).thenReturn(new com.aizuda.bpm.engine.entity.FlwTaskActor());
        return strategy;
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

    /** 桩实例/流程/任务列表；锁内行缺省与列表快照一致（无并发改动），需要差异时再 stubLockedTasks 覆盖 */
    private void stubApprovalTasks(FlwTask... tasks) {
        FlwInstance instance = runningInstance();
        when(queryService.getInstance(INSTANCE_ID)).thenReturn(instance);
        FlwProcess process = new FlwProcess();
        process.setId(10L);
        process.setProcessName("请假流程");
        when(processService.getProcessById(10L)).thenReturn(process);
        when(queryService.getTasksByInstanceId(INSTANCE_ID)).thenReturn(List.of(tasks));
        stubLockedTasks(tasks);
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
    @DisplayName("转办/条件审批类型的任务同样可催办")
    void urge_shouldUrgeTransferredAndConditionTasks() {
        FlwInstance instance = runningInstance();
        when(queryService.getInstance(INSTANCE_ID)).thenReturn(instance);
        FlwProcess process = new FlwProcess();
        process.setId(10L);
        process.setProcessName("请假流程");
        when(processService.getProcessById(10L)).thenReturn(process);
        when(identityService.getUserName(1L)).thenReturn("发起人");

        FlwTask transferred = new FlwTask();
        transferred.setId(1001L);
        transferred.setInstanceId(INSTANCE_ID);
        transferred.setTaskKey("node_2");
        transferred.setTaskName("审核人");
        transferred.setTaskType(TaskType.transfer.getValue());
        transferred.setCreateTime(new java.util.Date());
        FlwTask condition = new FlwTask();
        condition.setId(1002L);
        condition.setInstanceId(INSTANCE_ID);
        condition.setTaskKey("node_3");
        condition.setTaskName("条件审批");
        condition.setTaskType(TaskType.conditionNode.getValue());
        condition.setCreateTime(new java.util.Date());
        when(queryService.getTasksByInstanceId(INSTANCE_ID)).thenReturn(List.of(transferred, condition));
        when(queryService.getTaskActorsByTaskId(1001L)).thenReturn(List.of());
        when(queryService.getTaskActorsByTaskId(1002L)).thenReturn(List.of());
        stubLockedTasks(transferred, condition);

        try (var securityMock = mockStatic(SecurityHelper.class);
             var userContextMock = mockStatic(UserContext.class)) {
            securityMock.when(SecurityHelper::getCurrentUserId).thenReturn(1L);
            userContextMock.when(() -> UserContext.get()).thenReturn(null);

            // 无参与者仅校验不误报"无待审批任务"：两个任务均通过催办目标过滤并写节流标记
            service.urgeInstance(String.valueOf(INSTANCE_ID), null);
        }

        verify(taskService, times(2)).updateTaskById(any(), any());
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
        verify(eventPublisher, never()).publishEvent(any(com.forge.modules.workflow.listener.event.TaskNotificationEvent.class));
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

        // 仅用户参与者收到通知（角色池不直接推送）：断言发布的事件字段
        ArgumentCaptor<com.forge.modules.workflow.listener.event.TaskNotificationEvent> eventCaptor =
                ArgumentCaptor.forClass(com.forge.modules.workflow.listener.event.TaskNotificationEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        com.forge.modules.workflow.listener.event.TaskNotificationEvent event = eventCaptor.getValue();
        assertEquals(List.of(2L), event.userIds(), "仅用户参与者进入通知名单（角色池不直接推送）");
        assertTrue(event.title().contains("催办"));
        assertTrue(event.content().contains("今天必须处理"));
        assertEquals(1000L, event.taskId());

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

    @Test
    @DisplayName("催办前置行锁读：SELECT ... FOR UPDATE 锁定该任务行")
    void urge_shouldLockTaskRowForUpdate() {
        FlwTask task = approvalTask(null);
        stubApprovalTasks(task);

        try (var securityMock = mockStatic(SecurityHelper.class);
             var userContextMock = mockStatic(UserContext.class)) {
            securityMock.when(SecurityHelper::getCurrentUserId).thenReturn(1L);
            userContextMock.when(() -> UserContext.get()).thenReturn(null);

            service.urgeInstance(String.valueOf(INSTANCE_ID), null);
        }

        ArgumentCaptor<LambdaQueryWrapper<FlwTask>> wrapperCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(taskMapper).selectOne(wrapperCaptor.capture());
        // 当前读行锁：SQL 尾部拼 FOR UPDATE，条件锁定目标任务行
        String sqlSegment = wrapperCaptor.getValue().getSqlSegment();
        assertTrue(sqlSegment.endsWith("FOR UPDATE"), "锁读 SQL 须带 FOR UPDATE，实际: " + sqlSegment);
        assertTrue(wrapperCaptor.getValue().getParamNameValuePairs().containsValue(1000L),
                "锁读条件须命中目标任务");
    }

    @Test
    @DisplayName("并发催办双穿透防护：列表快照无标记但锁内最新行已被催办——按锁内值节流")
    void urge_shouldThrottleByLockedRowNotSnapshot() {
        // 列表快照：无 lastUrgeTime（并发另一请求的写入尚未反映在该快照）
        FlwTask snapshot = approvalTask(null);
        // 锁内当前读：另一催办 5 分钟前已写节流标记
        FlwTask locked = approvalTask("{\"lastUrgeTime\":" + (System.currentTimeMillis() - 5 * 60_000L) + "}");
        FlwInstance instance = runningInstance();
        when(queryService.getInstance(INSTANCE_ID)).thenReturn(instance);
        FlwProcess process = new FlwProcess();
        process.setId(10L);
        process.setProcessName("请假流程");
        when(processService.getProcessById(10L)).thenReturn(process);
        when(queryService.getTasksByInstanceId(INSTANCE_ID)).thenReturn(List.of(snapshot));
        stubLockedTasks(locked);

        try (var securityMock = mockStatic(SecurityHelper.class);
             var userContextMock = mockStatic(UserContext.class)) {
            securityMock.when(SecurityHelper::getCurrentUserId).thenReturn(1L);
            userContextMock.when(() -> UserContext.get()).thenReturn(null);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.urgeInstance(String.valueOf(INSTANCE_ID), null));
            assertEquals(400, ex.getCode());
            assertTrue(ex.getMessage().contains("催办过于频繁"));
        }
        // 未重复催办：不写标记、不通知、不留痕
        verify(taskService, never()).updateTaskById(any(), any());
        verify(eventPublisher, never()).publishEvent(any(com.forge.modules.workflow.listener.event.TaskNotificationEvent.class));
        verify(approvalCommentMapper, never()).insert(any(WfApprovalComment.class));
    }

    @Test
    @DisplayName("节流标记基于锁内 variable 合并写回：不丢并发写入的其他变量键")
    void urge_shouldMergeMarkerIntoLockedVariable() {
        FlwTask snapshot = approvalTask(null);
        FlwTask locked = approvalTask("{\"foo\":\"bar\"}");
        FlwInstance instance = runningInstance();
        when(queryService.getInstance(INSTANCE_ID)).thenReturn(instance);
        FlwProcess process = new FlwProcess();
        process.setId(10L);
        process.setProcessName("请假流程");
        when(processService.getProcessById(10L)).thenReturn(process);
        when(queryService.getTasksByInstanceId(INSTANCE_ID)).thenReturn(List.of(snapshot));
        stubLockedTasks(locked);
        when(identityService.getUserName(1L)).thenReturn("发起人");
        when(queryService.getTaskActorsByTaskId(1000L)).thenReturn(List.of());

        try (var securityMock = mockStatic(SecurityHelper.class);
             var userContextMock = mockStatic(UserContext.class)) {
            securityMock.when(SecurityHelper::getCurrentUserId).thenReturn(1L);
            userContextMock.when(() -> UserContext.get()).thenReturn(null);

            service.urgeInstance(String.valueOf(INSTANCE_ID), null);
        }

        // 写回的 variable 同时含既有键 foo 与新 lastUrgeTime（锁内行整体合并，非快照覆写）
        verify(taskService).updateTaskById(argThat(update ->
                update != null && update.getVariable() != null
                        && update.getVariable().contains("\"foo\":\"bar\"")
                        && update.getVariable().contains("lastUrgeTime")), any());
    }

    @Test
    @DisplayName("取锁时任务已办结（锁内读为空）：视同无待审批任务，不催办不写标记")
    void urge_shouldSkipTaskVanishedBeforeLock() {
        FlwTask snapshot = approvalTask(null);
        FlwInstance instance = runningInstance();
        when(queryService.getInstance(INSTANCE_ID)).thenReturn(instance);
        FlwProcess process = new FlwProcess();
        process.setId(10L);
        process.setProcessName("请假流程");
        when(processService.getProcessById(10L)).thenReturn(process);
        when(queryService.getTasksByInstanceId(INSTANCE_ID)).thenReturn(List.of(snapshot));
        // 行锁读未命中：加载到取锁之间任务已被办理移入历史表
        stubLockedTasks((FlwTask) null);

        try (var securityMock = mockStatic(SecurityHelper.class);
             var userContextMock = mockStatic(UserContext.class)) {
            securityMock.when(SecurityHelper::getCurrentUserId).thenReturn(1L);
            userContextMock.when(() -> UserContext.get()).thenReturn(null);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.urgeInstance(String.valueOf(INSTANCE_ID), null));
            assertEquals(400, ex.getCode());
            assertTrue(ex.getMessage().contains("无待审批任务"));
        }
        verify(taskService, never()).updateTaskById(any(), any());
        verify(eventPublisher, never()).publishEvent(any(com.forge.modules.workflow.listener.event.TaskNotificationEvent.class));
        verify(approvalCommentMapper, never()).insert(any(WfApprovalComment.class));
    }
}
