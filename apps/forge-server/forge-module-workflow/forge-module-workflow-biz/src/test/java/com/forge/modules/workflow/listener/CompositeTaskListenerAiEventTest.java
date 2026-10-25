package com.forge.modules.workflow.listener;

import com.aizuda.bpm.engine.FlowLongEngine;
import com.aizuda.bpm.engine.core.FlowCreator;
import com.aizuda.bpm.engine.core.enums.TaskEventType;
import com.aizuda.bpm.engine.entity.FlwTask;
import com.aizuda.bpm.engine.model.NodeModel;
import com.forge.modules.workflow.dto.ai.AiApprovalConfig;
import com.forge.modules.workflow.framework.ai.AiApprovalExecutor;
import com.forge.modules.workflow.listener.event.WorkflowAiApprovalEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * CompositeTaskListener AI审批事件发布单元测试
 *
 * 验证 create 事件仅发布 WorkflowAiApprovalEvent（不在事务内同步执行AI审批）
 */
class CompositeTaskListenerAiEventTest {

    private CompositeTaskListener newListener(ApplicationEventPublisher publisher,
                                              AiApprovalExecutor aiApprovalExecutor) {
        return new CompositeTaskListener(publisher,
                mock(TaskNotificationListener.class),
                aiApprovalExecutor,
                mock(FlowLongEngine.class));
    }

    @Test
    @DisplayName("create事件且节点配置AI审批时应发布事件")
    void notify_shouldPublishAiEventOnCreate() {
        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
        AiApprovalExecutor executor = mock(AiApprovalExecutor.class);
        CompositeTaskListener listener = newListener(publisher, executor);

        FlwTask task = new FlwTask();
        task.setId(1L);
        task.setTaskName("审批");
        NodeModel nodeModel = new NodeModel();
        FlowCreator creator = new FlowCreator("1", "张三");

        WorkflowAiApprovalEvent event = new WorkflowAiApprovalEvent(
                this, 1L, new AiApprovalConfig(), "1", "张三", null);
        when(executor.buildAiApprovalEvent(task, nodeModel, creator)).thenReturn(event);

        listener.notify(TaskEventType.create, () -> task, Collections.emptyList(), nodeModel, creator);

        verify(publisher).publishEvent(event);
        // 同步段不得直接执行AI审批
        verify(executor, never()).executeForEvent(any());
    }

    @Test
    @DisplayName("节点未配置AI审批时不发布事件")
    void notify_shouldSkipWhenNoAiConfig() {
        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
        AiApprovalExecutor executor = mock(AiApprovalExecutor.class);
        CompositeTaskListener listener = newListener(publisher, executor);

        FlwTask task = new FlwTask();
        task.setId(1L);
        when(executor.buildAiApprovalEvent(any(), any(), any())).thenReturn(null);

        listener.notify(TaskEventType.create, () -> task, List.of(), new NodeModel(), null);

        verify(publisher, never()).publishEvent(any(WorkflowAiApprovalEvent.class));
    }

    @Test
    @DisplayName("非create事件不触发AI审批")
    void notify_shouldIgnoreNonCreateEvent() {
        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
        AiApprovalExecutor executor = mock(AiApprovalExecutor.class);
        CompositeTaskListener listener = newListener(publisher, executor);

        FlwTask task = new FlwTask();
        task.setId(1L);

        listener.notify(TaskEventType.complete, () -> task, List.of(), new NodeModel(), null);

        verify(executor, never()).buildAiApprovalEvent(any(), any(), any());
        verify(publisher, never()).publishEvent(any(WorkflowAiApprovalEvent.class));
    }
}
