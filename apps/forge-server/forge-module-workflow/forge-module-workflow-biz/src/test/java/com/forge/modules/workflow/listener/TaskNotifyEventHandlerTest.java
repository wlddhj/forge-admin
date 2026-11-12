package com.forge.modules.workflow.listener;

import com.forge.framework.web.websocket.NotificationMessage;
import com.forge.framework.web.websocket.NotificationService;
import com.forge.modules.workflow.listener.event.TaskNotificationEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentMatcher;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * 催办通知监听器单元测试（评审 §4.4-W1-P1#3）：
 * AFTER_COMMIT 后逐用户发送；单用户失败不影响其余用户。
 */
@ExtendWith(MockitoExtension.class)
class TaskNotifyEventHandlerTest {

    @Mock
    private NotificationService notificationService;

    private TaskNotifyEventHandler handler;

    @BeforeEach
    void setUp() {
        handler = new TaskNotifyEventHandler(notificationService);
    }

    @Test
    @DisplayName("提交后逐用户发送，消息形态与发布快照一致")
    void shouldSendToEachUser() {
        handler.onUrgeEvent(new TaskNotificationEvent(1000L, 100L, List.of(2L, 3L),
                "【催办】待办任务处理提醒", "请及时处理"));

        verify(notificationService).sendToUser(eq(2L), org.mockito.ArgumentMatchers.argThat(
                (NotificationMessage msg) -> msg != null && msg.getTitle().contains("催办")
                        && msg.getContent().contains("请及时处理")));
        verify(notificationService).sendToUser(eq(3L), any(NotificationMessage.class));
    }

    @Test
    @DisplayName("单用户发送失败仅告警，其余用户继续发送")
    void singleFailureShouldNotAffectOthers() {
        doThrow(new RuntimeException("ws down")).when(notificationService).sendToUser(eq(2L), any());

        handler.onUrgeEvent(new TaskNotificationEvent(1000L, 100L, List.of(2L, 3L),
                "催办", "内容"));

        verify(notificationService).sendToUser(eq(3L), any(NotificationMessage.class));
        verify(notificationService, times(1)).sendToUser(eq(3L), any());
    }

    @Test
    @DisplayName("空名单不发送")
    void emptyUsersShouldSkip() {
        handler.onUrgeEvent(new TaskNotificationEvent(1000L, 100L, List.of(), "催办", "内容"));
        verify(notificationService, never()).sendToUser(anyLong(), any());
    }
}
