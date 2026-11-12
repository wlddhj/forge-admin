package com.forge.modules.workflow.service.impl;

import com.aizuda.bpm.engine.FlowLongEngine;
import com.aizuda.bpm.engine.ProcessService;
import com.aizuda.bpm.engine.QueryService;
import com.aizuda.bpm.engine.RuntimeService;
import com.aizuda.bpm.engine.TaskService;
import com.aizuda.bpm.engine.core.enums.ActorType;
import com.aizuda.bpm.engine.core.enums.PerformType;
import com.aizuda.bpm.engine.core.enums.TaskType;
import com.aizuda.bpm.engine.entity.FlwHisTask;
import com.aizuda.bpm.engine.entity.FlwHisTaskActor;
import com.aizuda.bpm.engine.entity.FlwInstance;
import com.aizuda.bpm.engine.entity.FlwTask;
import com.aizuda.bpm.engine.entity.FlwTaskActor;
import com.aizuda.bpm.mybatisplus.mapper.FlwHisInstanceMapper;
import com.aizuda.bpm.mybatisplus.mapper.FlwHisTaskActorMapper;
import com.aizuda.bpm.mybatisplus.mapper.FlwHisTaskMapper;
import com.aizuda.bpm.mybatisplus.mapper.FlwInstanceMapper;
import com.aizuda.bpm.mybatisplus.mapper.FlwTaskActorMapper;
import com.aizuda.bpm.mybatisplus.mapper.FlwTaskMapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import tools.jackson.databind.ObjectMapper;
import com.forge.common.exception.BusinessException;
import com.forge.common.utils.UserContext;
import com.forge.framework.security.utils.SecurityHelper;
import com.forge.framework.web.websocket.NotificationService;
import com.forge.modules.system.service.SysAttachmentService;
import com.forge.modules.workflow.config.FlowLongSchedulerProperties;
import com.forge.modules.workflow.dto.task.TaskCompleteRequest;
import com.forge.modules.workflow.framework.actor.ForgeTaskAccessStrategy;
import com.forge.modules.workflow.identity.FlowLongIdentityService;
import com.forge.modules.workflow.mapper.WfApprovalCommentMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 任务操作归属校验单元测试（安全堵口第一批 T1）：
 * 引擎对 reject/退回/撤回/加减签/抄送/委派/转办路径不做参与者校验，服务层统一把关。
 */
class WfTaskServiceOwnershipTest {

    private static final Long TASK_ID = 2000L;
    private static final Long INSTANCE_ID = 200L;
    /** 任务直系参与者 */
    private static final Long ACTOR_USER = 5L;
    /** 既非参与者也无管理身份的用户 */
    private static final Long OUTSIDER_USER = 9L;
    /** 候选组（角色）任务：组内用户 */
    private static final Long GROUP_MEMBER = 6L;
    private static final Long ROLE_ID = 3L;

    @BeforeAll
    static void initMybatisPlusTableInfo() {
        // MP 3.5.17：纯单测下 LambdaQueryWrapper 需要实体 TableInfo 缓存
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, FlwHisTaskActor.class);
    }

    private TaskService taskService;
    private QueryService queryService;
    private RuntimeService runtimeService;
    private FlwHisTaskMapper hisTaskMapper;
    private FlwHisTaskActorMapper hisTaskActorMapper;
    private FlwInstanceMapper instanceMapper;
    private FlowLongEngine flowLongEngine;
    private FlowLongIdentityService identityService;
    private WfTaskServiceImpl service;

    @BeforeEach
    void setUp() {
        taskService = mock(TaskService.class);
        queryService = mock(QueryService.class);
        runtimeService = mock(RuntimeService.class);
        hisTaskMapper = mock(FlwHisTaskMapper.class);
        hisTaskActorMapper = mock(FlwHisTaskActorMapper.class);
        instanceMapper = mock(FlwInstanceMapper.class);
        flowLongEngine = mock(FlowLongEngine.class);
        identityService = mock(FlowLongIdentityService.class);

        // 用真实访问策略验证归属语义（直系参与者/候选组/管理员豁免）
        service = new WfTaskServiceImpl(taskService, mock(ProcessService.class), runtimeService,
                queryService, identityService, mock(WfApprovalCommentMapper.class),
                mock(FlwTaskMapper.class), hisTaskMapper, mock(FlwTaskActorMapper.class),
                hisTaskActorMapper, instanceMapper, mock(com.aizuda.bpm.mybatisplus.mapper.FlwProcessMapper.class),
                mock(FlwHisInstanceMapper.class), flowLongEngine, mock(SysAttachmentService.class),
                mock(NotificationService.class), new FlowLongSchedulerProperties(), new ObjectMapper(),
                new ForgeTaskAccessStrategy(identityService));
    }

    @AfterEach
    void clearContext() {
        UserContext.clear();
    }

    private FlwTask taskWithDirectActor() {
        FlwTask task = new FlwTask();
        task.setId(TASK_ID);
        task.setInstanceId(INSTANCE_ID);
        task.setTaskKey("node_1");
        task.setTaskName("部门审批");
        task.setTaskType(TaskType.approval.getValue());
        task.setPerformType(PerformType.orSign.getValue());
        when(queryService.getTask(TASK_ID)).thenReturn(task);

        FlwTaskActor actor = new FlwTaskActor();
        actor.setTaskId(TASK_ID);
        actor.setActorId(String.valueOf(ACTOR_USER));
        actor.setActorName("参与者5");
        actor.setActorType(ActorType.user.getValue());
        when(queryService.getTaskActorsByTaskId(TASK_ID)).thenReturn(List.of(actor));
        return task;
    }

    private TaskCompleteRequest completeRequest() {
        TaskCompleteRequest request = new TaskCompleteRequest();
        request.setComment("驳回意见");
        return request;
    }

    @Test
    @DisplayName("非参与者驳回他人任务被拒（403），引擎不被调用")
    void reject_shouldDenyOutsider() {
        taskWithDirectActor();
        when(identityService.getUserName(ACTOR_USER)).thenReturn("参与者5");

        try (var securityMock = mockStatic(SecurityHelper.class)) {
            securityMock.when(SecurityHelper::getCurrentUserId).thenReturn(OUTSIDER_USER);
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.rejectTask(String.valueOf(TASK_ID), completeRequest()));
            assertEquals(403, ex.getCode());
            assertTrue(ex.getMessage().contains("无权操作该任务"));
        }
        verify(flowLongEngine, never()).executeRejectTask(any(), anyString(), any(), any(), anyBoolean());
    }

    @Test
    @DisplayName("任务直系参与者可驳回")
    void reject_shouldAllowDirectActor() {
        taskWithDirectActor();
        when(identityService.getUserName(ACTOR_USER)).thenReturn("参与者5");
        when(taskService.getTaskModel(TASK_ID)).thenReturn(null);

        try (var securityMock = mockStatic(SecurityHelper.class)) {
            securityMock.when(SecurityHelper::getCurrentUserId).thenReturn(ACTOR_USER);
            service.rejectTask(String.valueOf(TASK_ID), completeRequest());
        }
        verify(flowLongEngine).executeRejectTask(any(), any(), any(), any(), anyBoolean());
    }

    @Test
    @DisplayName("候选组（角色）成员可驳回组任务")
    void reject_shouldAllowGroupMember() {
        FlwTask task = new FlwTask();
        task.setId(TASK_ID);
        task.setInstanceId(INSTANCE_ID);
        task.setTaskKey("node_1");
        task.setTaskName("角色候选任务");
        task.setTaskType(TaskType.approval.getValue());
        task.setPerformType(PerformType.orSign.getValue());
        when(queryService.getTask(TASK_ID)).thenReturn(task);

        FlwTaskActor roleActor = new FlwTaskActor();
        roleActor.setTaskId(TASK_ID);
        roleActor.setActorId(String.valueOf(ROLE_ID));
        roleActor.setActorName("角色3");
        roleActor.setActorType(ActorType.role.getValue());
        when(queryService.getTaskActorsByTaskId(TASK_ID)).thenReturn(List.of(roleActor));
        when(identityService.getUserRoleIds(GROUP_MEMBER)).thenReturn(java.util.Set.of(ROLE_ID));
        when(identityService.getUserName(GROUP_MEMBER)).thenReturn("组员6");
        when(taskService.getTaskModel(TASK_ID)).thenReturn(null);

        try (var securityMock = mockStatic(SecurityHelper.class)) {
            securityMock.when(SecurityHelper::getCurrentUserId).thenReturn(GROUP_MEMBER);
            service.rejectTask(String.valueOf(TASK_ID), completeRequest());
        }
        verify(flowLongEngine).executeRejectTask(any(), any(), any(), any(), anyBoolean());
    }

    @Test
    @DisplayName("工作流管理员豁免可操作任意任务")
    void reject_shouldAllowWorkflowAdmin() {
        taskWithDirectActor();

        UserContext admin = new UserContext();
        admin.setUserId(OUTSIDER_USER);
        admin.setRoles(List.of(roleInfo("workflow_admin")));
        UserContext.set(admin);
        when(identityService.getUserName(OUTSIDER_USER)).thenReturn("管理员9");
        when(taskService.getTaskModel(TASK_ID)).thenReturn(null);

        try (var securityMock = mockStatic(SecurityHelper.class)) {
            securityMock.when(SecurityHelper::getCurrentUserId).thenReturn(OUTSIDER_USER);
            service.rejectTask(String.valueOf(TASK_ID), completeRequest());
        }
        verify(flowLongEngine).executeRejectTask(any(), any(), any(), any(), anyBoolean());
    }

    @Test
    @DisplayName("非处理人撤回他人已办任务被拒（403），引擎不被调用")
    void withdraw_shouldDenyOutsider() {
        FlwHisTask hisTask = new FlwHisTask();
        hisTask.setId(TASK_ID);
        hisTask.setInstanceId(INSTANCE_ID);
        hisTask.setTaskKey("node_1");
        hisTask.setTaskName("已办任务");
        when(hisTaskMapper.selectById(TASK_ID)).thenReturn(hisTask);

        FlwInstance active = new FlwInstance();
        active.setId(INSTANCE_ID);
        when(instanceMapper.selectById(INSTANCE_ID)).thenReturn(active);

        FlwHisTaskActor handler = new FlwHisTaskActor();
        handler.setTaskId(TASK_ID);
        handler.setActorId(String.valueOf(ACTOR_USER));
        handler.setActorName("处理人5");
        handler.setActorType(ActorType.user.getValue());
        when(hisTaskActorMapper.selectList(any())).thenReturn(List.of(handler));

        try (var securityMock = mockStatic(SecurityHelper.class)) {
            securityMock.when(SecurityHelper::getCurrentUserId).thenReturn(OUTSIDER_USER);
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.withdrawTask(String.valueOf(TASK_ID)));
            assertEquals(403, ex.getCode());
        }
        verify(taskService, never()).withdrawTask(anyLong(), any());
    }

    @Test
    @DisplayName("处理人本人可撤回")
    void withdraw_shouldAllowHandler() {
        FlwHisTask hisTask = new FlwHisTask();
        hisTask.setId(TASK_ID);
        hisTask.setInstanceId(INSTANCE_ID);
        hisTask.setTaskKey("node_1");
        hisTask.setTaskName("已办任务");
        when(hisTaskMapper.selectById(TASK_ID)).thenReturn(hisTask);

        FlwInstance active = new FlwInstance();
        active.setId(INSTANCE_ID);
        when(instanceMapper.selectById(INSTANCE_ID)).thenReturn(active);

        FlwHisTaskActor handler = new FlwHisTaskActor();
        handler.setTaskId(TASK_ID);
        handler.setActorId(String.valueOf(ACTOR_USER));
        handler.setActorName("处理人5");
        handler.setActorType(ActorType.user.getValue());
        when(hisTaskActorMapper.selectList(any())).thenReturn(List.of(handler));
        when(identityService.getUserName(ACTOR_USER)).thenReturn("处理人5");

        try (var securityMock = mockStatic(SecurityHelper.class)) {
            securityMock.when(SecurityHelper::getCurrentUserId).thenReturn(ACTOR_USER);
            service.withdrawTask(String.valueOf(TASK_ID));
        }
        verify(taskService).withdrawTask(anyLong(), any());
    }

    private UserContext.DataScopeRoleInfo roleInfo(String roleCode) {
        UserContext.DataScopeRoleInfo info = new UserContext.DataScopeRoleInfo();
        info.setRoleCode(roleCode);
        return info;
    }
}
