package com.forge.modules.workflow.listener;

import com.aizuda.bpm.engine.core.FlowCreator;
import com.aizuda.bpm.engine.core.enums.ActorType;
import com.aizuda.bpm.engine.core.enums.TaskEventType;
import com.aizuda.bpm.engine.entity.FlwTask;
import com.aizuda.bpm.engine.entity.FlwTaskActor;
import com.aizuda.bpm.engine.model.NodeModel;
import com.forge.framework.web.websocket.NotificationMessage;
import com.forge.framework.web.websocket.NotificationService;
import com.forge.modules.workflow.identity.FlowLongIdentityService;
import com.forge.modules.workflow.listener.event.WorkflowTaskEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * 任务通知处理器
 *
 * 注意：不再实现 TaskListener 接口，由 CompositeTaskListener 调用
 * 使用 @Service 注解，可以被依赖注入
 *
 * 支持的事件类型：
 * - create: 发送新任务通知
 * - complete: 任务完成通知
 * - reject: 驳回通知
 * - transfer: 转办通知
 * - delegate: 委派通知
 * - withdraw: 撤回通知
 * - timeout: 超时提醒
 * - autoComplete: 自动完成通知
 * - autoReject: 自动驳回通知
 *
 * @author forge-admin
 */
@Slf4j
@Service
public class TaskNotificationListener {

    private final NotificationService notificationService;
    private final FlowLongIdentityService identityService;
    private final ApplicationEventPublisher eventPublisher;

    public TaskNotificationListener(NotificationService notificationService,
                                     FlowLongIdentityService identityService,
                                     ApplicationEventPublisher eventPublisher) {
        this.notificationService = notificationService;
        this.identityService = identityService;
        this.eventPublisher = eventPublisher;
    }

    /**
     * 处理任务事件
     * 由 CompositeTaskListener 调用
     */
    public boolean notify(TaskEventType eventType, Supplier<FlwTask> supplier,
                          List<FlwTaskActor> taskActors, NodeModel nodeModel, FlowCreator flowCreator) {
        FlwTask task = supplier.get();
        if (task == null) {
            return false;
        }

        log.info("任务事件: type={}, taskId={}, taskName={}, operator={}",
                eventType.name(), task.getId(), task.getTaskName(),
                flowCreator != null ? flowCreator.getCreateBy() : "unknown");

        // 发布 Spring 事件供其他组件处理
        WorkflowTaskEvent event = new WorkflowTaskEvent(
                this, eventType, task, taskActors, nodeModel, flowCreator);
        eventPublisher.publishEvent(event);

        // 根据事件类型发送通知
        handleEventNotification(eventType, task, taskActors, flowCreator);

        // 返回 false 表示不干预任务流程
        return false;
    }

    /**
     * 根据事件类型发送通知
     */
    private void handleEventNotification(TaskEventType eventType, FlwTask task,
                                          List<FlwTaskActor> taskActors, FlowCreator flowCreator) {
        switch (eventType) {
            case create:
                sendTaskCreatedNotification(task, taskActors);
                break;
            case complete:
                sendTaskCompletedNotification(task, flowCreator);
                break;
            case reject:
                sendTaskRejectedNotification(task, flowCreator);
                break;
            case transfer:
                sendTaskTransferredNotification(task, taskActors);
                break;
            case delegate:
                sendTaskDelegatedNotification(task, taskActors);
                break;
            case delegateResolve:
                sendTaskDelegatedResolvedNotification(task, flowCreator);
                break;
            case withdraw:
                sendTaskWithdrawnNotification(task, flowCreator);
                break;
            case timeout:
                sendTaskTimeoutNotification(task, taskActors);
                break;
            case autoComplete:
                sendTaskAutoCompletedNotification(task);
                break;
            case autoReject:
                sendTaskAutoRejectedNotification(task);
                break;
            default:
                log.debug("未处理的通知事件类型: {}", eventType);
        }
    }

    /**
     * 发送任务创建通知
     */
    private void sendTaskCreatedNotification(FlwTask task, List<FlwTaskActor> taskActors) {
        String title = "新待办任务";
        String content = String.format("您有一个新的待办任务「%s」，请及时处理。", task.getTaskName());
        sendNotificationToActors(task, taskActors, title, content);
    }

    /**
     * 发送任务完成通知（给发起人）
     */
    private void sendTaskCompletedNotification(FlwTask task, FlowCreator flowCreator) {
        String title = "任务已完成";
        String content = String.format("任务「%s」已被 %s 完成审批。",
                task.getTaskName(),
                flowCreator != null ? flowCreator.getCreateBy() : "系统");
        // 发送给发起人（如果与处理人不同）
        if (flowCreator != null) {
            try {
                Long operatorId = Long.parseLong(flowCreator.getCreateId());
                NotificationMessage message = NotificationMessage.workflow(title, content, task.getId());
                notificationService.sendToUser(operatorId, message);
            } catch (NumberFormatException e) {
                log.warn("无法解析操作者ID: {}", flowCreator.getCreateId());
            }
        }
    }

    /**
     * 发送驳回通知（给发起人或上一节点处理人）
     */
    private void sendTaskRejectedNotification(FlwTask task, FlowCreator flowCreator) {
        String title = "任务被驳回";
        String content = String.format("任务「%s」已被 %s 驳回，请重新处理。",
                task.getTaskName(),
                flowCreator != null ? flowCreator.getCreateBy() : "系统");
        // 需要通知被驳回节点的处理人（这里简化处理，实际需要从流程历史获取）
        log.info("驳回通知: taskId={}, content={}", task.getId(), content);
    }

    /**
     * 发送转办通知
     */
    private void sendTaskTransferredNotification(FlwTask task, List<FlwTaskActor> taskActors) {
        String title = "任务已转办";
        String content = String.format("任务「%s」已转办给您，请及时处理。", task.getTaskName());
        sendNotificationToActors(task, taskActors, title, content);
    }

    /**
     * 发送委派通知
     */
    private void sendTaskDelegatedNotification(FlwTask task, List<FlwTaskActor> taskActors) {
        String title = "任务已委派";
        String content = String.format("任务「%s」已委派给您处理，完成后将归还给原处理人。", task.getTaskName());
        sendNotificationToActors(task, taskActors, title, content);
    }

    /**
     * 发送委派解决通知（归还给原处理人）
     */
    private void sendTaskDelegatedResolvedNotification(FlwTask task, FlowCreator flowCreator) {
        String title = "委派任务已处理";
        String content = String.format("委派任务「%s」已由 %s 处理完成，请继续审批。",
                task.getTaskName(),
                flowCreator != null ? flowCreator.getCreateBy() : "代理人");
        // 通知原处理人
        log.info("委派解决通知: taskId={}, content={}", task.getId(), content);
    }

    /**
     * 发送撤回通知
     */
    private void sendTaskWithdrawnNotification(FlwTask task, FlowCreator flowCreator) {
        String title = "任务已撤回";
        String content = String.format("任务「%s」已被发起人撤回。", task.getTaskName());
        // 通知当前处理人
        log.info("撤回通知: taskId={}, operator={}", task.getId(),
                flowCreator != null ? flowCreator.getCreateBy() : "unknown");
    }

    /**
     * 发送超时提醒通知
     */
    private void sendTaskTimeoutNotification(FlwTask task, List<FlwTaskActor> taskActors) {
        String title = "【紧急】任务即将超时";
        String content = String.format("任务「%s」即将超时，请尽快处理！", task.getTaskName());
        sendNotificationToActors(task, taskActors, title, content);
    }

    /**
     * 发送自动完成通知
     */
    private void sendTaskAutoCompletedNotification(FlwTask task) {
        String title = "任务自动完成";
        String content = String.format("任务「%s」因超时已自动通过。", task.getTaskName());
        log.info("自动完成通知: taskId={}, content={}", task.getId(), content);
    }

    /**
     * 发送自动驳回通知
     */
    private void sendTaskAutoRejectedNotification(FlwTask task) {
        String title = "任务自动驳回";
        String content = String.format("任务「%s」因超时已自动驳回。", task.getTaskName());
        log.info("自动驳回通知: taskId={}, content={}", task.getId(), content);
    }

    /**
     * 发送通知给候选人
     */
    private void sendNotificationToActors(FlwTask task, List<FlwTaskActor> taskActors,
                                           String title, String content) {
        try {
            Set<Long> candidateUserIds = taskActors.stream()
                    .filter(actor -> ActorType.user.eq(actor.getActorType()))
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

            for (Long userId : candidateUserIds) {
                NotificationMessage message = NotificationMessage.workflow(title, content, task.getId());
                notificationService.sendToUser(userId, message);
            }

            log.info("发送通知: taskId={}, title={}, recipients={}", task.getId(), title, candidateUserIds);
        } catch (Exception e) {
            log.error("发送通知失败: taskId={}, error={}", task.getId(), e.getMessage(), e);
        }
    }
}