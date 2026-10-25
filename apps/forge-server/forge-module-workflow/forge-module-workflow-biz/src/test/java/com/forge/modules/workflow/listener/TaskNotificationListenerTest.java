package com.forge.modules.workflow.listener;

import com.aizuda.bpm.engine.core.FlowCreator;
import com.aizuda.bpm.engine.core.enums.ActorType;
import com.aizuda.bpm.engine.core.enums.TaskEventType;
import com.aizuda.bpm.engine.entity.FlwInstance;
import com.aizuda.bpm.engine.entity.FlwTask;
import com.aizuda.bpm.engine.entity.FlwTaskActor;
import com.aizuda.bpm.engine.model.NodeModel;
import com.aizuda.bpm.mybatisplus.mapper.FlwHisTaskActorMapper;
import com.aizuda.bpm.mybatisplus.mapper.FlwInstanceMapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.forge.framework.web.websocket.NotificationMessage;
import com.forge.framework.web.websocket.NotificationService;
import com.forge.modules.workflow.identity.FlowLongIdentityService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * 任务通知补齐单元测试：驳回→发起人、撤回→原处理人、完成→发起人
 */
class TaskNotificationListenerTest {

    private NotificationService notificationService;
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
        notificationService = mock(NotificationService.class);
        instanceMapper = mock(FlwInstanceMapper.class);
        hisTaskActorMapper = mock(FlwHisTaskActorMapper.class);
        listener = new TaskNotificationListener(notificationService,
                mock(FlowLongIdentityService.class), mock(ApplicationEventPublisher.class),
                instanceMapper, hisTaskActorMapper);
    }

    private FlwTask task() {
        FlwTask task = new FlwTask();
        task.setId(1000L);
        task.setInstanceId(100L);
        task.setTaskName("部门审批");
        return task;
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

        verify(notificationService).sendToUser(eq(1L), argThat(message ->
                message != null && message.getContent().contains("审批人乙")));
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

        verify(notificationService, times(1)).sendToUser(anyLong(), any(NotificationMessage.class));
        verify(notificationService).sendToUser(eq(1L), any(NotificationMessage.class));
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

        verify(notificationService).sendToUser(eq(5L), argThat(message ->
                message != null && message.getTitle().contains("撤回")));
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

        verify(notificationService).sendToUser(eq(6L), argThat(message ->
                message != null && message.getContent().contains("被委派人")));
    }

    @Test
    @DisplayName("无参与者时静默跳过通知")
    void shouldSkipWhenNoRecipients() {
        FlwTask task = task();
        when(hisTaskActorMapper.selectList(any())).thenReturn(Collections.emptyList());

        listener.notify(TaskEventType.withdraw, () -> task, null, null, null);

        verify(notificationService, never()).sendToUser(anyLong(), any());
    }
}
