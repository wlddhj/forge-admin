package com.forge.modules.workflow.framework.ai;

import com.aizuda.bpm.engine.FlowLongEngine;
import com.aizuda.bpm.engine.QueryService;
import com.aizuda.bpm.engine.core.FlowCreator;
import com.aizuda.bpm.engine.entity.FlwTask;
import com.aizuda.bpm.engine.model.NodeModel;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.forge.modules.workflow.dto.ai.AiApprovalConfig;
import com.forge.modules.workflow.dto.ai.AiApprovalResult;
import com.forge.modules.workflow.dto.ai.AiApprovalResult.AiStatus;
import com.forge.modules.workflow.dto.ai.AiApprovalResult.Decision;
import com.forge.modules.workflow.entity.WfAiApprovalRecord;
import com.forge.modules.workflow.framework.FlowCreatorFactory;
import com.forge.modules.workflow.listener.event.WorkflowAiApprovalEvent;
import com.forge.modules.workflow.mapper.WfAiApprovalRecordMapper;
import com.forge.modules.workflow.mapper.WfApprovalCommentMapper;
import com.forge.modules.workflow.service.WfAiApprovalService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * AI审批异步化单元测试
 *
 * 覆盖：事件构建（同步段）、幂等守卫、超时降级、人工决策留存
 */
class AiApprovalExecutorTest {

    private WfAiApprovalService aiApprovalService;
    private FlowLongEngine flowLongEngine;
    private QueryService queryService;
    private WfAiApprovalRecordMapper aiApprovalRecordMapper;
    private AiApprovalExecutor executor;

    @BeforeEach
    void setUp() {
        aiApprovalService = mock(WfAiApprovalService.class);
        flowLongEngine = mock(FlowLongEngine.class);
        queryService = mock(QueryService.class);
        aiApprovalRecordMapper = mock(WfAiApprovalRecordMapper.class);
        when(flowLongEngine.queryService()).thenReturn(queryService);
        executor = new AiApprovalExecutor(aiApprovalService, flowLongEngine,
                aiApprovalRecordMapper, mock(WfApprovalCommentMapper.class), new ObjectMapper());
    }

    private AiApprovalConfig enabledConfig() {
        AiApprovalConfig config = new AiApprovalConfig();
        config.setEnabled(true);
        config.setProvider("glm");
        config.setModelName("glm-4");
        return config;
    }

    private FlwTask task(Long id) {
        FlwTask task = new FlwTask();
        task.setId(id);
        task.setInstanceId(100L);
        task.setTaskName("部门审批");
        task.setTaskKey("node_1");
        return task;
    }

    @Test
    @DisplayName("节点未启用AI审批时构建事件返回null")
    void buildAiApprovalEvent_shouldReturnNullWhenDisabled() {
        NodeModel nodeModel = new NodeModel();
        nodeModel.setNodeKey("node_1");
        when(aiApprovalService.parseAiConfig(any())).thenReturn(null);

        assertNull(executor.buildAiApprovalEvent(task(1L), nodeModel, FlowCreatorFactory.system("系统")));
    }

    @Test
    @DisplayName("启用AI审批时事件携带配置与操作人快照")
    void buildAiApprovalEvent_shouldSnapshotOperator() {
        AiApprovalConfig config = enabledConfig();
        when(aiApprovalService.parseAiConfig(any())).thenReturn(config);

        FlwTask task = task(9L);
        WorkflowAiApprovalEvent event = executor.buildAiApprovalEvent(task, new NodeModel(),
                new FlowCreator("42", "张三"));

        assertNotNull(event);
        assertEquals(9L, event.getTaskId());
        assertSame(config, event.getAiConfig());
        assertEquals("42", event.getOperatorId());
        assertEquals("张三", event.getOperatorName());
    }

    @Test
    @DisplayName("任务已被人工办理（不在活动表）时放弃AI审批")
    void executeForEvent_shouldSkipWhenTaskGone() {
        when(queryService.getTask(1L)).thenReturn(null);

        WorkflowAiApprovalEvent event = new WorkflowAiApprovalEvent(this, 1L, enabledConfig(), "1", "张三", null);
        executor.executeForEvent(event);

        verify(aiApprovalService, never()).executeAiApproval(any(), any(), any(), any(), any());
        verify(aiApprovalRecordMapper, never()).insert(any(WfAiApprovalRecord.class));
    }

    @Test
    @DisplayName("AI决策为人工处理时仅留存记录，不触发引擎动作")
    void executeForEvent_shouldKeepTaskWhenManual() {
        FlwTask task = task(1L);
        when(queryService.getTask(1L)).thenReturn(task);
        when(queryService.getInstance(100L)).thenReturn(null);
        AiApprovalResult manual = new AiApprovalResult();
        manual.setDecision(Decision.MANUAL);
        manual.setStatus(AiStatus.LOW_CONFIDENCE);
        manual.setReasoning("置信度不足");
        when(aiApprovalService.executeAiApproval(any(), any(), any(), any(), any())).thenReturn(manual);

        executor.executeForEvent(new WorkflowAiApprovalEvent(this, 1L, enabledConfig(), "1", "张三", null));

        verify(flowLongEngine, never()).executeTask(anyLong(), any(), any());
        ArgumentCaptor<WfAiApprovalRecord> captor = ArgumentCaptor.forClass(WfAiApprovalRecord.class);
        verify(aiApprovalRecordMapper).insert(captor.capture());
        assertEquals("MANUAL", captor.getValue().getDecision());
    }

    @Test
    @DisplayName("LLM调用超时时按fallback降级且不自动执行审批")
    void executeForEvent_shouldFallbackOnTimeout() {
        FlwTask task = task(1L);
        when(queryService.getTask(1L)).thenReturn(task);
        when(queryService.getInstance(100L)).thenReturn(null);

        // 模拟慢调用：睡 1.5s，超时配置 1s
        when(aiApprovalService.executeAiApproval(any(), any(), any(), any(), any()))
                .thenAnswer(inv -> {
                    TimeUnit.MILLISECONDS.sleep(1500);
                    return new AiApprovalResult();
                });
        AiApprovalResult timeoutResult = new AiApprovalResult();
        timeoutResult.setDecision(Decision.MANUAL);
        timeoutResult.setStatus(AiStatus.TIMEOUT);
        when(aiApprovalService.buildFallbackResult(any(), eq(AiStatus.TIMEOUT), anyString())).thenReturn(timeoutResult);

        AiApprovalConfig config = enabledConfig();
        config.setTimeoutSeconds(1);
        executor.executeForEvent(new WorkflowAiApprovalEvent(this, 1L, config, "1", "张三", null));

        verify(aiApprovalService).buildFallbackResult(eq(config), eq(AiStatus.TIMEOUT), anyString());
        verify(flowLongEngine, never()).executeTask(anyLong(), any(), any());
        verify(aiApprovalRecordMapper).insert(any(WfAiApprovalRecord.class));
    }

    @Test
    @DisplayName("FlowCreatorFactory.restore 保留数字操作人，缺失ID时回落系统创建者")
    void flowCreatorRestore_shouldHandleOperatorSnapshot() {
        FlowCreator user = FlowCreatorFactory.restore("42", "张三");
        assertEquals("42", user.getCreateId());
        assertEquals("张三", user.getCreateBy());

        FlowCreator system = FlowCreatorFactory.restore(null, null);
        assertEquals("SYSTEM", system.getCreateId());
    }
}
