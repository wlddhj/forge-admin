package com.forge.modules.workflow.listener;

import com.forge.framework.web.websocket.NotificationMessage;
import com.forge.framework.web.websocket.NotificationService;
import com.forge.modules.workflow.identity.FlowableIdentityService;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.delegate.TaskListener;
import org.flowable.engine.impl.el.FixedValue;
import org.flowable.task.service.delegate.DelegateTask;
import org.springframework.stereotype.Component;

/**
 * 任务通知监听器
 * 当任务创建时，通过 WebSocket 向处理人发送通知
 *
 * @author forge-admin
 */
@Slf4j
@Component("taskNotificationListener")
public class TaskNotificationListener implements TaskListener {

    private final NotificationService notificationService;
    private final FlowableIdentityService flowableIdentityService;

    public TaskNotificationListener(NotificationService notificationService,
                                     FlowableIdentityService flowableIdentityService) {
        this.notificationService = notificationService;
        this.flowableIdentityService = flowableIdentityService;
    }

    @Override
    public void notify(DelegateTask delegateTask) {
        String eventName = delegateTask.getEventName();
//        if (!EVENTNAME_CREATE.equals(eventName)) {
//            return;
//        }

        try {
            String assignee = delegateTask.getAssignee();
            String taskName = delegateTask.getName();
            String processInstanceId = delegateTask.getProcessInstanceId();

            if (assignee != null && !assignee.isEmpty()) {
                Long assigneeId;
                try {
                    assigneeId = Long.parseLong(assignee);
                } catch (NumberFormatException e) {
                    log.warn("任务处理人ID格式错误：assignee={}", assignee);
                    return;
                }

                String title = "新待办任务";
                String content = String.format("您有一个新的待办任务「%s」，请及时处理。", taskName);

                NotificationMessage message = NotificationMessage.workflow(title, content, Long.valueOf(delegateTask.getId().hashCode()).longValue());
                notificationService.sendToUser(assigneeId, message);

                log.info("发送任务通知：taskId={}, assignee={}, taskName={}",
                        delegateTask.getId(), assignee, taskName);
            }
        } catch (Exception e) {
            log.error("发送任务通知失败：taskId={}, error={}", delegateTask.getId(), e.getMessage(), e);
        }
    }
}
