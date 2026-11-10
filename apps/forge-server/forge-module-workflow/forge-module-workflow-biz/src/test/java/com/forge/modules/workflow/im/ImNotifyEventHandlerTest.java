package com.forge.modules.workflow.im;

import com.aizuda.bpm.engine.ProcessService;
import com.aizuda.bpm.engine.QueryService;
import com.aizuda.bpm.engine.core.enums.ActorType;
import com.aizuda.bpm.engine.core.enums.InstanceEventType;
import com.aizuda.bpm.engine.core.enums.TaskEventType;
import com.aizuda.bpm.engine.entity.FlwHisInstance;
import com.aizuda.bpm.engine.entity.FlwInstance;
import com.aizuda.bpm.engine.entity.FlwProcess;
import com.aizuda.bpm.engine.entity.FlwTask;
import com.aizuda.bpm.engine.entity.FlwTaskActor;
import com.forge.modules.workflow.framework.event.ReliableEventConsumer;
import com.forge.modules.workflow.listener.event.WorkflowInstanceEvent;
import com.forge.modules.workflow.listener.event.WorkflowTaskEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 事件处理器单测：事件类型过滤、参与者解析、发起人解析失败跳过、文案路由
 */
class ImNotifyEventHandlerTest {

    private ReliableEventConsumer consumer;
    private ImNotificationRouter router;
    private ImMessageBuilder messageBuilder;
    private ProcessService processService;
    private QueryService queryService;
    private ImNotifyEventHandler handler;

    @BeforeEach
    void setUp() {
        consumer = mock(ReliableEventConsumer.class);
        // consume 直跑 Runnable（与真实重试模板的立即首试一致）
        doAnswer(invocation -> {
            ((Runnable) invocation.getArgument(2)).run();
            return null;
        }).when(consumer).consume(anyString(), any(), any(Runnable.class));

        router = mock(ImNotificationRouter.class);
        messageBuilder = mock(ImMessageBuilder.class);
        processService = mock(ProcessService.class);
        queryService = mock(QueryService.class);
        handler = new ImNotifyEventHandler(consumer, router, messageBuilder, processService, queryService);
    }

    private FlwTaskActor actor(String id) {
        FlwTaskActor actor = new FlwTaskActor();
        actor.setActorId(id);
        actor.setActorType(ActorType.user.getValue());
        return actor;
    }

    private WorkflowTaskEvent taskEvent(TaskEventType type, List<FlwTaskActor> actors) {
        FlwTask task = new FlwTask();
        task.setId(11L);
        task.setTaskName("审批节点A");
        task.setInstanceId(22L);
        task.setTaskKey("node_a");
        return new WorkflowTaskEvent(this, type, task, actors, null, null);
    }

    @Test
    void onTaskEvent_Create_DispatchesToActors() {
        FlwInstance instance = new FlwInstance();
        instance.setProcessId(33L);
        when(queryService.getInstance(22L)).thenReturn(instance);
        FlwProcess process = new FlwProcess();
        process.setProcessKey("demo_process");
        process.setProcessName("项目立项");
        when(processService.getProcessById(33L)).thenReturn(process);
        ImNotificationMessage msg = new ImNotificationMessage("taskCreated", "t", "c", "u", 11L, 22L, "demo_process");
        when(messageBuilder.buildTaskMessage(eq(TaskEventType.create), any(), eq("demo_process"), eq("项目立项")))
                .thenReturn(msg);

        handler.onTaskEvent(taskEvent(TaskEventType.create, List.of(actor("1"), actor("2"), actor("abc"))));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Long>> usersCaptor = ArgumentCaptor.forClass((Class<List<Long>>) (Class<?>) List.class);
        verify(router).dispatch(eq(msg), usersCaptor.capture());
        // 非数字 actorId（如角色/部门 actor）被跳过
        assertEquals(List.of(1L, 2L), usersCaptor.getValue());
    }

    @Test
    void onTaskEvent_Complete_NotPushed() {
        handler.onTaskEvent(taskEvent(TaskEventType.complete, List.of(actor("1"))));
        verify(router, never()).dispatch(any(), anyCollection());
    }

    @Test
    void onTaskEvent_Reject_NotPushed() {
        handler.onTaskEvent(taskEvent(TaskEventType.reject, List.of(actor("1"))));
        verify(router, never()).dispatch(any(), anyCollection());
    }

    @Test
    void onTaskEvent_Timeout_Dispatched() {
        when(queryService.getInstance(22L)).thenReturn(null);
        ImNotificationMessage msg = new ImNotificationMessage("taskTimeout", "t", "c", "u", 11L, 22L, null);
        when(messageBuilder.buildTaskMessage(eq(TaskEventType.timeout), any(), eq(null), eq(null)))
                .thenReturn(msg);

        handler.onTaskEvent(taskEvent(TaskEventType.timeout, List.of(actor("3"))));

        verify(router).dispatch(eq(msg), eq(List.of(3L)));
    }

    @Test
    void onTaskEvent_NoUserActors_NoDispatch() {
        handler.onTaskEvent(taskEvent(TaskEventType.create, List.of()));
        verify(router, never()).dispatch(any(), anyCollection());
    }

    @Test
    void onInstanceEvent_Terminal_NotifiesInitiator() {
        FlwHisInstance his = new FlwHisInstance();
        his.setId(22L);
        his.setCreateId("9");
        his.setProcessId(33L);
        WorkflowInstanceEvent event = new WorkflowInstanceEvent(this, InstanceEventType.end, his, null, null);
        when(processService.getProcessById(33L)).thenReturn(null);
        ImNotificationMessage msg = new ImNotificationMessage("instanceFinished", "t", "c", "u", null, 22L, null);
        when(messageBuilder.buildInstanceMessage(eq(InstanceEventType.end), eq(his), eq(null), eq(null)))
                .thenReturn(msg);

        handler.onInstanceEvent(event);

        verify(router).dispatch(eq(msg), eq(List.of(9L)));
    }

    @Test
    void onInstanceEvent_NonTerminal_Ignored() {
        FlwHisInstance his = new FlwHisInstance();
        his.setId(22L);
        WorkflowInstanceEvent event = new WorkflowInstanceEvent(this, InstanceEventType.start, his, null, null);
        handler.onInstanceEvent(event);
        verify(router, never()).dispatch(any(), anyCollection());
    }

    @Test
    void onInstanceEvent_InitiatorNotParsable_Skips() {
        FlwHisInstance his = new FlwHisInstance();
        his.setId(22L);
        his.setCreateId("not-a-number");
        WorkflowInstanceEvent event = new WorkflowInstanceEvent(this, InstanceEventType.end, his, null, null);

        handler.onInstanceEvent(event);

        verify(router, never()).dispatch(any(), anyCollection());
        assertTrue(true);
    }
}
