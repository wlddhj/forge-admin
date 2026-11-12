package com.forge.modules.workflow.listener;

import com.aizuda.bpm.engine.core.FlowCreator;
import com.aizuda.bpm.engine.core.enums.ActorType;
import com.aizuda.bpm.engine.core.enums.TaskEventType;
import com.aizuda.bpm.engine.entity.FlwInstance;
import com.aizuda.bpm.engine.entity.FlwTask;
import com.aizuda.bpm.engine.entity.FlwTaskActor;
import com.aizuda.bpm.mybatisplus.mapper.FlwHisTaskActorMapper;
import com.aizuda.bpm.mybatisplus.mapper.FlwInstanceMapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.forge.modules.workflow.identity.FlowLongIdentityService;
import com.forge.modules.workflow.listener.event.TaskNotificationEvent;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 任务通知补齐单元测试：驳回→发起人、撤回→原处理人、完成→发起人。
 * 通知事件化（评审 §4.4-W1-P1#3 扩展）后断言发布的事件字段（AFTER_COMMIT
 * 监听器 TaskNotifyEventHandler 另有单测）。
 */
class TaskNotificationListenerTest {

    private ApplicationEventPublisher eventPublisher;
    private FlwInstanceMapper instanceMapper;
    private FlwHisTaskActorMapper hisTaskActorMapper;
    private TaskNotificationListener listener;

    @BeforeAll
    static void initMybatisPlusTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, com.aizuda.bpm.engine.entity.FlwHisTaskActor.class);
    }

    @BeforeEach
    void setUp() {
        eventPublisher = mock(ApplicationEventPublisher.class);
        instanceMapper = mock(FlwInstanceMapper.class);
        hisTaskActorMapper = mock(FlwHisTaskActorMapper.class);
        listener = new TaskNotificationListener(mock(FlowLongIdentityService.class), eventPublisher,
                instanceMapper, hisTaskActorMapper);
    }

    private FlwTask task() {
        FlwTask task = new FlwTask();
        task.setId(1000L);
        task.setInstanceId(100L);
        task.setTaskName("部门审批");
        return task;
    }

    private TaskNotificationEvent capturedEvent() {
        ArgumentCaptor<TaskNotificationEvent> captor = ArgumentCaptor.forClass(TaskNotificationEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("驳回事件通知发起人")
    void reject_shouldNotifyInitiator() {
        FlwTask task = task();
        FlwInstance instance = new FlwInstance();
        instance.setId(100L);
        instance.setCreateId("1");
        when(instanceMapper.selectById(100L)).thenReturn(instance);

        listener.notify(TaskEventType.reject, () -> task, List.of(), null,
                new FlowCreator("2", "审批人乙"));

        TaskNotificationEvent event = capturedEvent();
        assertEquals(List.of(1L), event.userIds());
        assertTrue(event.content().contains("审批人乙"));
    }

    @Test
    @DisplayName("完成事件通知发起人而非操作人")
    void complete_shouldNotifyInitiatorNotOperator() {
        FlwTask task = task();
        FlwInstance instance = new FlwInstance();
        instance.setId(100L);
        instance.setCreateId("1");
        when(instanceMapper.selectById(100L)).thenReturn(instance);

        listener.notify(TaskEventType.complete, () -> task, List.of(), null,
                new FlowCreator("2", "审批人乙"));

        TaskNotificationEvent event = capturedEvent();
        assertEquals(List.of(1L), event.userIds(), "通知发起人而非操作人");
    }

    @Test
    @DisplayName("撤回事件（taskActors为空）回退查历史参与者并通知原处理人")
    void withdraw_shouldFallbackToHisActors() {
        FlwTask task = task();
        com.aizuda.bpm.engine.entity.FlwHisTaskActor hisActor = new com.aizuda.bpm.engine.entity.FlwHisTaskActor();
        hisActor.setTaskId(1000L);
        hisActor.setActorId("5");
        hisActor.setActorName("原处理人");
        hisActor.setActorType(ActorType.user.getValue());
        when(hisTaskActorMapper.selectList(any())).thenReturn(List.of(hisActor));

        listener.notify(TaskEventType.withdraw, () -> task, null, null,
                new FlowCreator("1", "发起人"));

        TaskNotificationEvent event = capturedEvent();
        assertEquals(List.of(5L), event.userIds());
        assertTrue(event.title().contains("撤回"));
    }

    @Test
    @DisplayName("委派归还事件通知还原后的原委托人")
    void delegateResolve_shouldNotifyAssignor() {
        FlwTask task = task();
        FlwTaskActor restoredAssignor = new FlwTaskActor();
        restoredAssignor.setTaskId(1000L);
        restoredAssignor.setActorId("6");
        restoredAssignor.setActorName("原委托人");
        restoredAssignor.setActorType(ActorType.user.getValue());

        listener.notify(TaskEventType.delegateResolve, () -> task, List.of(restoredAssignor), null,
                new FlowCreator("7", "被委派人"));

        TaskNotificationEvent event = capturedEvent();
        assertEquals(List.of(6L), event.userIds());
        assertTrue(event.content().contains("被委派人"));
    }

    @Test
    @DisplayName("无参与者时静默跳过通知")
    void shouldSkipWhenNoRecipients() {
        FlwTask task = task();
        when(hisTaskActorMapper.selectList(any())).thenReturn(Collections.emptyList());

        listener.notify(TaskEventType.withdraw, () -> task, null, null, null);

        verify(eventPublisher, never()).publishEvent(any(TaskNotificationEvent.class));
    }
}
