package com.forge.modules.workflow.listener;

import com.forge.framework.web.websocket.NotificationMessage;
import com.forge.framework.web.websocket.NotificationService;
import com.forge.modules.workflow.listener.event.TaskNotificationEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 催办通知事件监听器（评审 §4.4-W1-P1#3）：
 * 事务提交后逐用户发送 WebSocket 催办提醒——回滚不产生幽灵通知，
 * 慢发送不占用催办事务线程。逐用户 try-catch，单用户失败不影响其余。
 *
 * @author forge-admin
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TaskNotifyEventHandler {

    private final NotificationService notificationService;

    @Async("workflowExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onUrgeEvent(TaskNotificationEvent event) {
        for (Long userId : event.userIds()) {
            try {
                notificationService.sendToUser(userId,
                        NotificationMessage.workflow(event.title(), event.content(), event.taskId()));
            } catch (Exception e) {
                log.warn("催办通知发送失败: taskId={}, userId={}", event.taskId(), userId, e);
            }
        }
    }
}
