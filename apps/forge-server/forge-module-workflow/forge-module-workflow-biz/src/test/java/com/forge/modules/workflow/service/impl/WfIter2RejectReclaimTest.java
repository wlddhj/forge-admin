package com.forge.modules.workflow.service.impl;

import com.aizuda.bpm.engine.FlowLongEngine;
import com.aizuda.bpm.engine.ProcessService;
import com.aizuda.bpm.engine.QueryService;
import com.aizuda.bpm.engine.RuntimeService;
import com.aizuda.bpm.engine.TaskService;
import com.aizuda.bpm.engine.core.enums.TaskType;
import com.aizuda.bpm.engine.entity.FlwHisTask;
import com.aizuda.bpm.engine.entity.FlwHisTaskActor;
import com.aizuda.bpm.engine.entity.FlwTask;
import com.aizuda.bpm.engine.model.NodeModel;
import com.aizuda.bpm.mybatisplus.mapper.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.forge.common.exception.BusinessException;
import com.forge.framework.security.utils.SecurityHelper;
import com.forge.framework.web.websocket.NotificationService;
import com.forge.modules.system.service.SysAttachmentService;
import com.forge.modules.workflow.config.FlowLongSchedulerProperties;
import com.forge.modules.workflow.dto.task.TaskCompleteRequest;
import com.forge.modules.workflow.identity.FlowLongIdentityService;
import com.forge.modules.workflow.mapper.WfApprovalCommentMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 迭代二：驳回策略分派 + 拿回校验单元测试
 */
class WfIter2RejectReclaimTest {

    private static final Long TASK_ID = 1000L;

    private TaskService taskService;
    private QueryService queryService;
    private FlowLongEngine flowLongEngine;
    private FlwHisTaskMapper hisTaskMapper;
    private FlwHisTaskActorMapper hisTaskActorMapper;
    private WfTaskServiceImpl service;

    @BeforeEach
    void setUp() {
        taskService = mock(TaskService.class);
        queryService = mock(QueryService.class);
        flowLongEngine = mock(FlowLongEngine.class);
        hisTaskMapper = mock(FlwHisTaskMapper.class);
        hisTaskActorMapper = mock(FlwHisTaskActorMapper.class);

        service = new WfTaskServiceImpl(taskService, mock(ProcessService.class), mock(RuntimeService.class),
                queryService, mock(FlowLongIdentityService.class), mock(WfApprovalCommentMapper.class),
                mock(FlwTaskMapper.class), hisTaskMapper, mock(FlwTaskActorMapper.class),
                hisTaskActorMapper, mock(FlwInstanceMapper.class), mock(FlwHisInstanceMapper.class),
                flowLongEngine, mock(SysAttachmentService.class), mock(NotificationService.class),
                new FlowLongSchedulerProperties(), new ObjectMapper());
    }

    private FlwTask approvalTask() {
        FlwTask task = new FlwTask();
        task.setId(TASK_ID);
        task.setInstanceId(100L);
        task.setTaskKey("node_1");
        task.setTaskName("审批");
        task.setTaskType(TaskType.approval.getValue());
        when(queryService.getTask(TASK_ID)).thenReturn(task);
        return task;
    }

    private void stubRejectStrategy(Integer strategy, String targetKey) {
        NodeModel nodeModel = new NodeModel();
        nodeModel.setNodeKey("node_1");
        nodeModel.setRejectStrategy(strategy);
        if (targetKey != null) {
            java.util.Map<String, Object> ext = new HashMap<>();
            ext.put("rejectTargetKey", targetKey);
            nodeModel.setExtendConfig(ext);
        }
        when(taskService.getTaskModel(TASK_ID)).thenReturn(nodeModel);
    }

    private TaskCompleteRequest request() {
        return new TaskCompleteRequest();
    }

    @Test
    @DisplayName("未配置驳回策略维持既有行为：直接终止")
    void reject_shouldTerminateWhenNoStrategy() {
        approvalTask();
        stubRejectStrategy(null, null);

        try (var securityMock = mockStatic(SecurityHelper.class)) {
            securityMock.when(SecurityHelper::getCurrentUserId).thenReturn(1L);
            service.rejectTask(String.valueOf(TASK_ID), request());
        }

        verify(flowLongEngine).executeRejectTask(any(), isNull(), any(), any(), eq(true));
    }

    @Test
    @DisplayName("策略1/2交引擎分派：不终止不指定节点")
    void reject_shouldDelegateToEngineByStrategy() {
        approvalTask();
        stubRejectStrategy(2, null);

        try (var securityMock = mockStatic(SecurityHelper.class)) {
            securityMock.when(SecurityHelper::getCurrentUserId).thenReturn(1L);
            service.rejectTask(String.valueOf(TASK_ID), request());
        }

        verify(flowLongEngine).executeRejectTask(any(), isNull(), any(), any(), eq(false));
    }

    @Test
    @DisplayName("策略3未配置目标节点时报错")
    void reject_shouldRejectWhenStrategy3WithoutTarget() {
        approvalTask();
        stubRejectStrategy(3, null);

        try (var securityMock = mockStatic(SecurityHelper.class)) {
            securityMock.when(SecurityHelper::getCurrentUserId).thenReturn(1L);
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.rejectTask(String.valueOf(TASK_ID), request()));
            assertEquals(400, ex.getCode());
        }
        verify(flowLongEngine, never()).executeRejectTask(any(), any(), any(), any(), anyBoolean());
    }

    @Test
    @DisplayName("策略3按 extendConfig.rejectTargetKey 跳转")
    void reject_shouldJumpToTargetKeyForStrategy3() {
        approvalTask();
        stubRejectStrategy(3, "node_9");

        try (var securityMock = mockStatic(SecurityHelper.class)) {
            securityMock.when(SecurityHelper::getCurrentUserId).thenReturn(1L);
            service.rejectTask(String.valueOf(TASK_ID), request());
        }

        verify(flowLongEngine).executeRejectTask(any(), eq("node_9"), any(), any(), eq(false));
    }

    @Test
    @DisplayName("拿回：非任务办理人被拒绝")
    void reclaim_shouldRejectNonHandler() {
        FlwHisTask hisTask = new FlwHisTask();
        hisTask.setId(TASK_ID);
        hisTask.setTaskName("审批");
        when(hisTaskMapper.selectById(TASK_ID)).thenReturn(hisTask);
        when(hisTaskActorMapper.selectCount(any())).thenReturn(0L);

        try (var securityMock = mockStatic(SecurityHelper.class)) {
            securityMock.when(SecurityHelper::getCurrentUserId).thenReturn(7L);
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.reclaimTask(String.valueOf(TASK_ID)));
            assertEquals(403, ex.getCode());
        }
        verify(taskService, never()).reclaimTask(anyLong(), any(), any());
    }

    @Test
    @DisplayName("拿回：办理人拿回成功并留痕")
    void reclaim_shouldSucceedForHandler() {
        FlwHisTask hisTask = new FlwHisTask();
        hisTask.setId(TASK_ID);
        hisTask.setInstanceId(100L);
        hisTask.setTaskKey("node_1");
        hisTask.setTaskName("审批");
        hisTask.setTaskType(TaskType.approval.getValue());
        when(hisTaskMapper.selectById(TASK_ID)).thenReturn(hisTask);
        when(hisTaskActorMapper.selectCount(any())).thenReturn(1L);

        try (var securityMock = mockStatic(SecurityHelper.class)) {
            securityMock.when(SecurityHelper::getCurrentUserId).thenReturn(1L);
            service.reclaimTask(String.valueOf(TASK_ID));
        }

        verify(taskService).reclaimTask(eq(TASK_ID), any(), isNull());
    }

    @Test
    @DisplayName("拿回：引擎拒绝（下一节点已办）转为业务提示")
    void reclaim_shouldTranslateEngineRejection() {
        FlwHisTask hisTask = new FlwHisTask();
        hisTask.setId(TASK_ID);
        hisTask.setTaskName("审批");
        when(hisTaskMapper.selectById(TASK_ID)).thenReturn(hisTask);
        when(hisTaskActorMapper.selectCount(any())).thenReturn(1L);
        when(taskService.reclaimTask(anyLong(), any(), any()))
                .thenThrow(new com.aizuda.bpm.engine.exception.FlowLongException("Do not allow reclaim task"));

        try (var securityMock = mockStatic(SecurityHelper.class)) {
            securityMock.when(SecurityHelper::getCurrentUserId).thenReturn(1L);
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.reclaimTask(String.valueOf(TASK_ID)));
            assertEquals(400, ex.getCode());
        }
    }
}
