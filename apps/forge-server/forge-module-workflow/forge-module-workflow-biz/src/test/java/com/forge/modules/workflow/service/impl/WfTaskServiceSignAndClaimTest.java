package com.forge.modules.workflow.service.impl;

import com.aizuda.bpm.engine.FlowLongEngine;
import com.aizuda.bpm.engine.ProcessService;
import com.aizuda.bpm.engine.QueryService;
import com.aizuda.bpm.engine.RuntimeService;
import com.aizuda.bpm.engine.TaskService;
import com.aizuda.bpm.engine.core.enums.ActorType;
import com.aizuda.bpm.engine.core.enums.AgentType;
import com.aizuda.bpm.engine.core.enums.PerformType;
import com.aizuda.bpm.engine.core.enums.TaskType;
import com.aizuda.bpm.engine.entity.*;
import com.aizuda.bpm.mybatisplus.mapper.*;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.forge.common.exception.BusinessException;
import com.forge.framework.security.utils.SecurityHelper;
import com.forge.framework.web.websocket.NotificationService;
import com.forge.modules.system.service.SysAttachmentService;
import com.forge.modules.workflow.config.FlowLongSchedulerProperties;
import com.forge.modules.workflow.dto.task.TaskQueryRequest;
import com.forge.modules.workflow.dto.task.TaskResponse;
import com.forge.modules.workflow.dto.task.TaskSignCreateRequest;
import com.forge.modules.workflow.dto.task.TaskSignDeleteRequest;
import com.forge.modules.workflow.identity.FlowLongIdentityService;
import com.forge.modules.workflow.mapper.WfApprovalCommentMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 加签增强与抢办池单元测试
 */
class WfTaskServiceSignAndClaimTest {

    private static final Long TASK_ID = 1000L;
    private static final Long INSTANCE_ID = 100L;

    @BeforeAll
    static void initMybatisPlusTableInfo() {
        // MP 3.5.17：纯单测下 LambdaQueryWrapper 需要实体 TableInfo 缓存
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, FlwTaskActor.class);
        TableInfoHelper.initTableInfo(assistant, FlwTask.class);
    }

    private TaskService taskService;
    private QueryService queryService;
    private FlowLongIdentityService identityService;
    private FlwTaskMapper taskMapper;
    private FlwTaskActorMapper taskActorMapper;
    private FlowLongEngine flowLongEngine;
    private WfTaskServiceImpl service;

    @BeforeEach
    void setUp() {
        taskService = mock(TaskService.class);
        queryService = mock(QueryService.class);
        identityService = mock(FlowLongIdentityService.class);
        taskMapper = mock(FlwTaskMapper.class);
        taskActorMapper = mock(FlwTaskActorMapper.class);
        flowLongEngine = mock(FlowLongEngine.class);

        service = new WfTaskServiceImpl(taskService, mock(ProcessService.class), mock(RuntimeService.class),
                queryService, identityService, mock(WfApprovalCommentMapper.class),
                taskMapper, mock(FlwHisTaskMapper.class), taskActorMapper,
                mock(FlwHisTaskActorMapper.class), mock(FlwInstanceMapper.class),
                mock(FlwHisInstanceMapper.class), flowLongEngine, mock(SysAttachmentService.class),
                mock(NotificationService.class), new FlowLongSchedulerProperties(), new ObjectMapper());
    }

    private FlwTask orSignTask() {
        FlwTask task = new FlwTask();
        task.setId(TASK_ID);
        task.setInstanceId(INSTANCE_ID);
        task.setTaskKey("node_1");
        task.setTaskName("部门审批");
        task.setTaskType(TaskType.approval.getValue());
        task.setPerformType(PerformType.orSign.getValue());
        when(queryService.getTask(TASK_ID)).thenReturn(task);
        return task;
    }

    private TaskSignCreateRequest signRequest(String type) {
        TaskSignCreateRequest request = new TaskSignCreateRequest();
        request.setTaskId(String.valueOf(TASK_ID));
        request.setType(type);
        request.setUserIds(List.of("9"));
        request.setReason("补充审批");
        return request;
    }

    @Test
    @DisplayName("并行加签保持任务原参与方式（或签不强转会签）")
    void parallelSign_shouldKeepOriginalPerformType() {
        orSignTask();
        when(queryService.getTasksByInstanceIdAndTaskKey(INSTANCE_ID, "node_1")).thenReturn(List.of());
        when(identityService.getUserName(9L)).thenReturn("用户9");
        when(identityService.getUserName(1L)).thenReturn("操作人");

        try (var securityMock = mockStatic(SecurityHelper.class)) {
            securityMock.when(SecurityHelper::getCurrentUserId).thenReturn(1L);
            service.signCreateTask(String.valueOf(TASK_ID), signRequest("parallel"));
        }

        verify(taskService).addTaskActor(eq(TASK_ID), eq(PerformType.orSign), anyList(), any());
        verify(flowLongEngine, never()).executeAppendNodeModel(anyLong(), any(), any(), any(), anyBoolean());
    }

    @Test
    @DisplayName("并行加签所选用户已是参与人时明确报错")
    void parallelSign_shouldRejectDuplicateActor() {
        FlwTask task = orSignTask();
        when(queryService.getTasksByInstanceIdAndTaskKey(INSTANCE_ID, "node_1")).thenReturn(List.of(task));
        FlwTaskActor existing = new FlwTaskActor();
        existing.setTaskId(TASK_ID);
        existing.setActorId("9");
        existing.setActorName("用户9");
        existing.setActorType(ActorType.user.getValue());
        when(queryService.getTaskActorsByTaskId(TASK_ID)).thenReturn(List.of(existing));
        when(identityService.getUserName(1L)).thenReturn("操作人");

        try (var securityMock = mockStatic(SecurityHelper.class)) {
            securityMock.when(SecurityHelper::getCurrentUserId).thenReturn(1L);
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.signCreateTask(String.valueOf(TASK_ID), signRequest("parallel")));
            assertEquals(400, ex.getCode());
            assertTrue(ex.getMessage().contains("已是当前任务参与人"));
        }
        verify(taskService, never()).addTaskActor(anyLong(), any(), anyList(), any());
    }

    @Test
    @DisplayName("前加签构造临时审批节点并按 before 语义插入")
    void beforeSign_shouldAppendTempNodeBefore() throws Exception {
        orSignTask();
        when(identityService.getUserName(9L)).thenReturn("用户9");
        when(identityService.getUserName(1L)).thenReturn("操作人");

        TaskSignCreateRequest request = signRequest("before");
        request.setExamineMode(3);
        try (var securityMock = mockStatic(SecurityHelper.class)) {
            securityMock.when(SecurityHelper::getCurrentUserId).thenReturn(1L);
            service.signCreateTask(String.valueOf(TASK_ID), request);
        }

        ArgumentCaptor<com.aizuda.bpm.engine.model.NodeModel> nodeCaptor =
                ArgumentCaptor.forClass(com.aizuda.bpm.engine.model.NodeModel.class);
        verify(flowLongEngine).executeAppendNodeModel(eq(TASK_ID), nodeCaptor.capture(), any(), isNull(), eq(true));
        com.aizuda.bpm.engine.model.NodeModel node = nodeCaptor.getValue();
        assertEquals(TaskType.approval.getValue(), node.getType());
        assertEquals(3, node.getExamineMode());
        assertEquals(1, node.getNodeAssigneeList().size());
        assertEquals("9", node.getNodeAssigneeList().get(0).getId());
        verify(taskService, never()).addTaskActor(anyLong(), any(), anyList(), any());
    }

    @Test
    @DisplayName("后加签按 after 语义插入且默认会签")
    void afterSign_shouldAppendTempNodeAfter() {
        orSignTask();
        when(identityService.getUserName(9L)).thenReturn("用户9");
        when(identityService.getUserName(1L)).thenReturn("操作人");

        try (var securityMock = mockStatic(SecurityHelper.class)) {
            securityMock.when(SecurityHelper::getCurrentUserId).thenReturn(1L);
            service.signCreateTask(String.valueOf(TASK_ID), signRequest("after"));
        }

        verify(flowLongEngine).executeAppendNodeModel(eq(TASK_ID), any(), any(), isNull(), eq(false));
    }

    @Test
    @DisplayName("非法加签类型被拒绝")
    void signCreate_shouldRejectUnknownType() {
        orSignTask();
        when(identityService.getUserName(1L)).thenReturn("操作人");

        try (var securityMock = mockStatic(SecurityHelper.class)) {
            securityMock.when(SecurityHelper::getCurrentUserId).thenReturn(1L);
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.signCreateTask(String.valueOf(TASK_ID), signRequest("whatever")));
            assertEquals(400, ex.getCode());
        }
    }

    @Test
    @DisplayName("减签按被减签用户操作并传参 actorId")
    void signDelete_shouldPassActorId() {
        orSignTask();
        when(identityService.getUserName(1L)).thenReturn("操作人");
        when(identityService.getUserName(9L)).thenReturn("用户9");

        TaskSignDeleteRequest request = new TaskSignDeleteRequest();
        request.setTaskId(String.valueOf(TASK_ID));
        request.setActorId("9");

        try (var securityMock = mockStatic(SecurityHelper.class)) {
            securityMock.when(SecurityHelper::getCurrentUserId).thenReturn(1L);
            service.signDeleteTask(String.valueOf(TASK_ID), request);
        }

        verify(taskService).removeTaskActor(eq(TASK_ID), eq(List.of("9")), any());
    }

    @Test
    @DisplayName("子任务列表返回当前节点各任务的用户参与者")
    void childTasks_shouldListUserActorsOfNodeTasks() {
        FlwTask task = orSignTask();
        FlwTask sibling = new FlwTask();
        sibling.setId(1001L);
        sibling.setTaskKey("node_1");
        sibling.setPerformType(PerformType.countersign.getValue());
        when(queryService.getTasksByInstanceIdAndTaskKey(INSTANCE_ID, "node_1"))
                .thenReturn(List.of(task, sibling));

        FlwTaskActor userActor = new FlwTaskActor();
        userActor.setTaskId(1001L);
        userActor.setActorId("9");
        userActor.setActorName("用户9");
        userActor.setActorType(ActorType.user.getValue());
        FlwTaskActor roleActor = new FlwTaskActor();
        roleActor.setTaskId(1001L);
        roleActor.setActorId("30");
        roleActor.setActorName("经理角色");
        roleActor.setActorType(ActorType.role.getValue());
        when(queryService.getTaskActorsByTaskId(TASK_ID)).thenReturn(List.of());
        when(queryService.getTaskActorsByTaskId(1001L)).thenReturn(List.of(userActor, roleActor));

        List<Map<String, String>> children = service.getChildTasks(String.valueOf(TASK_ID));

        assertEquals(1, children.size(), "仅返回用户参与者");
        assertEquals("9", children.get(0).get("actorId"));
        assertEquals("用户9", children.get(0).get("actorName"));
        assertEquals("1001", children.get(0).get("taskId"));
    }

    @Test
    @DisplayName("可签收查询：用户角色命中候选组并标记待认领")
    void claimable_shouldMatchGroupActors() {
        com.forge.common.utils.UserContext.DataScopeRoleInfo roleInfo = new com.forge.common.utils.UserContext.DataScopeRoleInfo();
        roleInfo.setRoleId(30L);
        com.forge.common.utils.UserContext userContext = new com.forge.common.utils.UserContext();
        userContext.setRoles(List.of(roleInfo));

        try (var securityMock = mockStatic(SecurityHelper.class);
             var userContextMock = mockStatic(com.forge.common.utils.UserContext.class)) {
            securityMock.when(SecurityHelper::getCurrentUserId).thenReturn(7L);
            userContextMock.when(() -> com.forge.common.utils.UserContext.get()).thenReturn(userContext);
            when(identityService.getUserDeptId(7L)).thenReturn(null);

            FlwTaskActor roleActor = new FlwTaskActor();
            roleActor.setTaskId(TASK_ID);
            roleActor.setActorId("30");
            roleActor.setActorName("经理角色");
            roleActor.setActorType(ActorType.role.getValue());
            when(taskActorMapper.selectList(any())).thenReturn(List.of(roleActor));

            FlwTask task = orSignTask();
            Page<FlwTask> page = new Page<>(1, 1, 1);
            page.setRecords(List.of(task));
            when(taskMapper.selectPage(any(), any())).thenReturn(page);
            FlwInstance instance = new FlwInstance();
            instance.setId(INSTANCE_ID);
            when(queryService.getInstance(INSTANCE_ID)).thenReturn(instance);

            TaskQueryRequest request = new TaskQueryRequest();
            Page<TaskResponse> result = service.getClaimableTasks(request);

            assertEquals(1, result.getRecords().size());
            assertTrue(result.getRecords().get(0).getCandidate());
            assertEquals(List.of("经理角色"), result.getRecords().get(0).getCandidateUsers());
        }
    }

    @Test
    @DisplayName("取消签收：还原原候选组参与者")
    void unclaim_shouldRestoreGroupActor() {
        FlwTask task = orSignTask();

        FlwTaskActor claimed = new FlwTaskActor();
        claimed.setTaskId(TASK_ID);
        claimed.setActorId("7");
        claimed.setActorName("认领人");
        claimed.setActorType(ActorType.user.getValue());
        claimed.setAgentId("30");
        claimed.setAgentType(AgentType.claimRole.getValue());
        claimed.setExt("{\"actorType\":1,\"actorName\":\"经理角色\"}");
        when(queryService.getTaskActorsByTaskId(TASK_ID)).thenReturn(List.of(claimed));

        try (var securityMock = mockStatic(SecurityHelper.class)) {
            securityMock.when(SecurityHelper::getCurrentUserId).thenReturn(7L);
            service.unclaimTask(String.valueOf(TASK_ID));
        }

        ArgumentCaptor<FlwTaskActor> captor = ArgumentCaptor.forClass(FlwTaskActor.class);
        verify(taskService).changeTaskActor(eq(TASK_ID), captor.capture());
        FlwTaskActor restored = captor.getValue();
        assertEquals("30", restored.getActorId());
        assertEquals(ActorType.role.getValue(), restored.getActorType());
        assertEquals("经理角色", restored.getActorName());
        assertEquals(TASK_ID, restored.getTaskId());
        assertEquals(INSTANCE_ID, restored.getInstanceId());
    }

    @Test
    @DisplayName("取消签收：非本人候选组签收的任务被拒绝")
    void unclaim_shouldRejectWhenNotClaimedByUser() {
        orSignTask();
        FlwTaskActor plainActor = new FlwTaskActor();
        plainActor.setTaskId(TASK_ID);
        plainActor.setActorId("7");
        plainActor.setActorType(ActorType.user.getValue());
        when(queryService.getTaskActorsByTaskId(TASK_ID)).thenReturn(List.of(plainActor));

        try (var securityMock = mockStatic(SecurityHelper.class)) {
            securityMock.when(SecurityHelper::getCurrentUserId).thenReturn(7L);
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.unclaimTask(String.valueOf(TASK_ID)));
            assertEquals(400, ex.getCode());
        }
    }
}
