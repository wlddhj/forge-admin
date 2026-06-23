package com.forge.modules.workflow.listener;

import com.aizuda.bpm.engine.core.FlowCreator;
import com.aizuda.bpm.engine.core.enums.TaskEventType;
import com.aizuda.bpm.engine.entity.FlwTask;
import com.aizuda.bpm.engine.entity.FlwTaskActor;
import com.aizuda.bpm.engine.listener.TaskListener;
import com.aizuda.bpm.engine.model.NodeModel;
import com.forge.framework.web.websocket.NotificationMessage;
import com.forge.framework.web.websocket.NotificationService;
import com.forge.modules.workflow.identity.FlowLongIdentityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * 任务通知监听器 - FlowLong 版本
 * 当任务创建时，通过 WebSocket 向处理人发送通知
 *
 * @author forge-admin
 */
@Slf4j
@Component("taskNotificationListener")
public class TaskNotificationListener implements TaskListener {

    private final NotificationService notificationService;
    private final FlowLongIdentityService identityService;

    public TaskNotificationListener(NotificationService notificationService,
                                     FlowLongIdentityService identityService) {
        this.notificationService = notificationService;
        this.identityService = identityService;
    }

    @Override
    public boolean notify(TaskEventType eventType, Supplier<FlwTask> supplier,
                          List<FlwTaskActor> taskActors, NodeModel nodeModel, FlowCreator flowCreator) {
        // 只处理任务创建事件
        if (eventType != TaskEventType.create) {
            return false;
        }

        FlwTask task = supplier.get();
        if (task == null) {
            return false;
        }

        sendNotification(task, taskActors);
        return false; // 返回 false 表示不干预任务流程
    }

    /**
     * 发送任务通知
     */
    private void sendNotification(FlwTask task, List<FlwTaskActor> taskActors) {
        try {
            String taskName = task.getTaskName();
            Long instanceId = task.getInstanceId();

            // 获取候选人用户ID
            Set<Long> candidateUserIds = taskActors.stream()
                    .filter(actor -> actor.getActorType() == 0) // 用户类型
                    .map(actor -> {
                        try {
                            return Long.parseLong(actor.getActorId());
                        } catch (NumberFormatException e) {
                            return null;
                        }
                    })
                    .filter(id -> id != null)
                    .collect(Collectors.toSet());

            if (candidateUserIds.isEmpty()) {
                log.debug("任务 {} 没有候选用户，跳过通知", task.getId());
                return;
            }

            String title = "新待办任务";
            String content = String.format("您有一个新的待办任务「%s」，请及时处理。", taskName);

            // 向所有候选用户发送通知
            for (Long userId : candidateUserIds) {
                NotificationMessage message = NotificationMessage.workflow(title, content, task.getId());
                notificationService.sendToUser(userId, message);
            }

            log.info("发送任务通知：taskId={}, taskName={}, candidates={}",
                    task.getId(), taskName, candidateUserIds);
        } catch (Exception e) {
            log.error("发送任务通知失败：taskId={}, error={}", task.getId(), e.getMessage(), e);
        }
    }
}