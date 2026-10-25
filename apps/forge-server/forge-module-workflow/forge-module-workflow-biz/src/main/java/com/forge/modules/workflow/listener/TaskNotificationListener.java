package com.forge.modules.workflow.listener;

import cn.hutool.core.util.StrUtil;
import com.aizuda.bpm.engine.core.FlowCreator;
import com.aizuda.bpm.engine.core.enums.ActorType;
import com.aizuda.bpm.engine.core.enums.TaskEventType;
import com.aizuda.bpm.engine.entity.FlwInstance;
import com.aizuda.bpm.engine.entity.FlwTask;
import com.aizuda.bpm.engine.entity.FlwTaskActor;
import com.aizuda.bpm.engine.model.NodeModel;
import com.aizuda.bpm.mybatisplus.mapper.FlwHisTaskActorMapper;
import com.aizuda.bpm.mybatisplus.mapper.FlwInstanceMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
 * - complete: 任务完成通知（通知发起人）
 * - reject: 驳回通知（通知发起人）
 * - transfer: 转办通知
 * - delegate: 委派通知
 * - delegateResolve: 委派归还通知（通知原委托人）
 * - withdraw: 撤回通知（通知被撤回任务的原处理人）
 * - timeout: 超时提醒
 * - autoComplete: 自动完成通知（通知任务参与者）
 * - autoReject: 自动驳回通知（通知任务参与者）
 *
 * @author forge-admin
 */
@Slf4j
@Service
public class TaskNotificationListener {

    private final NotificationService notificationService;
    private final FlowLongIdentityService identityService;
    private final ApplicationEventPublisher eventPublisher;
    private final FlwInstanceMapper instanceMapper;
    private final FlwHisTaskActorMapper hisTaskActorMapper;

    public TaskNotificationListener(NotificationService notificationService,
                                     FlowLongIdentityService identityService,
                                     ApplicationEventPublisher eventPublisher,
                                     FlwInstanceMapper instanceMapper,
                                     FlwHisTaskActorMapper hisTaskActorMapper) {
        this.notificationService = notificationService;
        this.identityService = identityService;
        this.eventPublisher = eventPublisher;
        this.instanceMapper = instanceMapper;
        this.hisTaskActorMapper = hisTaskActorMapper;
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
                sendTaskDelegatedResolvedNotification(task, taskActors, flowCreator);
                break;
            case withdraw:
                sendTaskWithdrawnNotification(task, taskActors, flowCreator);
                break;
            case timeout:
                sendTaskTimeoutNotification(task, taskActors);
                break;
            case autoComplete:
                sendTaskAutoCompletedNotification(task, taskActors);
                break;
            case autoReject:
                sendTaskAutoRejectedNotification(task, taskActors);
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
        String content = String.format("您的流程任务「%s」已被 %s 完成审批。",
                task.getTaskName(),
                flowCreator != null ? flowCreator.getCreateBy() : "系统");
        notifyInitiator(task, title, content);
    }

    /**
     * 发送驳回通知（给发起人）
     */
    private void sendTaskRejectedNotification(FlwTask task, FlowCreator flowCreator) {
        String title = "流程被驳回";
        String content = String.format("您的流程任务「%s」已被 %s 驳回，请处理后重新提交。",
                task.getTaskName(),
                flowCreator != null ? flowCreator.getCreateBy() : "系统");
        notifyInitiator(task, title, content);
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
    private void sendTaskDelegatedResolvedNotification(FlwTask task, List<FlwTaskActor> taskActors, FlowCreator flowCreator) {
        String title = "委派任务已处理";
        String content = String.format("委派任务「%s」已由 %s 处理完成并归还给您，请继续审批。",
                task.getTaskName(),
                flowCreator != null ? flowCreator.getCreateBy() : "代理人");
        // 委派归还后任务参与者已还原为原委托人
        sendNotificationToActors(task, resolveEventActors(task, taskActors), title, content);
    }

    /**
     * 发送撤回通知（给被撤回任务的原处理人）
     */
    private void sendTaskWithdrawnNotification(FlwTask task, List<FlwTaskActor> taskActors, FlowCreator flowCreator) {
        String title = "任务已撤回";
        String content = String.format("任务「%s」已被 %s 撤回，该任务从您的待办中移除。",
                task.getTaskName(),
                flowCreator != null ? flowCreator.getCreateBy() : "系统");
        // 撤回事件引擎传参 taskActors=null：查历史参与者通知原处理人
        sendNotificationToActors(task, resolveEventActors(task, taskActors), title, content);
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
    private void sendTaskAutoCompletedNotification(FlwTask task, List<FlwTaskActor> taskActors) {
        String title = "任务自动完成";
        String content = String.format("任务「%s」因超时已自动通过。", task.getTaskName());
        sendNotificationToActors(task, resolveEventActors(task, taskActors), title, content);
    }

    /**
     * 发送自动驳回通知
     */
    private void sendTaskAutoRejectedNotification(FlwTask task, List<FlwTaskActor> taskActors) {
        String title = "任务自动驳回";
        String content = String.format("任务「%s」因超时已自动驳回。", task.getTaskName());
        sendNotificationToActors(task, resolveEventActors(task, taskActors), title, content);
    }

    /**
     * 通知流程发起人（实例不存在或发起人不可解析时跳过）
     */
    private void notifyInitiator(FlwTask task, String title, String content) {
        try {
            FlwInstance instance = instanceMapper.selectById(task.getInstanceId());
            if (instance == null || StrUtil.isBlank(instance.getCreateId())) {
                return;
            }
            Long initiatorId = Long.parseLong(instance.getCreateId());
            notificationService.sendToUser(initiatorId, NotificationMessage.workflow(title, content, task.getId()));
        } catch (NumberFormatException e) {
            log.warn("发起人ID不可解析，跳过通知: instanceId={}", task.getInstanceId());
        } catch (Exception e) {
            log.error("发送发起人通知失败: taskId={}", task.getId(), e);
        }
    }

    /**
     * 事件参与者：优先用引擎传参，缺失时回退查询历史参与者（撤回等场景引擎传 null）
     */
    private List<FlwTaskActor> resolveEventActors(FlwTask task, List<FlwTaskActor> taskActors) {
        if (taskActors != null && !taskActors.isEmpty()) {
            return taskActors;
        }
        LambdaQueryWrapper<com.aizuda.bpm.engine.entity.FlwHisTaskActor> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(com.aizuda.bpm.engine.entity.FlwHisTaskActor::getTaskId, task.getId());
        List<com.aizuda.bpm.engine.entity.FlwHisTaskActor> hisActors = hisTaskActorMapper.selectList(wrapper);
        return hisActors.stream().map(hisActor -> (FlwTaskActor) hisActor).collect(Collectors.toList());
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