package com.forge.modules.workflow.im;

import com.aizuda.bpm.engine.ProcessService;
import com.aizuda.bpm.engine.QueryService;
import com.aizuda.bpm.engine.core.enums.ActorType;
import com.aizuda.bpm.engine.core.enums.InstanceEventType;
import com.aizuda.bpm.engine.core.enums.TaskEventType;
import com.aizuda.bpm.engine.entity.FlwInstance;
import com.aizuda.bpm.engine.entity.FlwProcess;
import com.aizuda.bpm.engine.entity.FlwTask;
import com.aizuda.bpm.engine.entity.FlwTaskActor;
import com.forge.modules.workflow.framework.event.ReliableEventConsumer;
import com.forge.modules.workflow.listener.event.WorkflowInstanceEvent;
import com.forge.modules.workflow.listener.event.WorkflowTaskEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * IM 工作通知事件处理器：消费工作流 Spring 事件（事务提交后异步），
 * 推送企业微信/钉钉工作通知。任何异常自行吞掉，绝不影响审批主流程。
 * <p>
 * 事件范围（02 设计定稿）：task create/recreate（新待办→审批人）、task timeout（超时提醒→审批人）、
 * 实例 7 终态（结果→发起人）。中间过程事件（complete/reject/transfer 等）不推，避免噪音。
 *
 * @author forge-admin
 */
@Slf4j
@Component
public class ImNotifyEventHandler {

    /**
     * 实例终态集合（与 BusinessCallbackEventListener.TERMINAL 同集）
     */
    private static final Set<InstanceEventType> TERMINAL_INSTANCE_EVENTS = EnumSet.of(
            InstanceEventType.forceComplete, InstanceEventType.rejectComplete,
            InstanceEventType.revokeComplete, InstanceEventType.timeoutComplete,
            InstanceEventType.autoComplete, InstanceEventType.autoReject, InstanceEventType.end);

    private final ReliableEventConsumer consumer;
    private final ImNotificationRouter router;
    private final ImMessageBuilder messageBuilder;
    private final ProcessService processService;
    private final QueryService queryService;

    public ImNotifyEventHandler(ReliableEventConsumer consumer,
                                ImNotificationRouter router,
                                ImMessageBuilder messageBuilder,
                                ProcessService processService,
                                QueryService queryService) {
        this.consumer = consumer;
        this.router = router;
        this.messageBuilder = messageBuilder;
        this.processService = processService;
        this.queryService = queryService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Async("workflowExecutor")
    public void onTaskEvent(WorkflowTaskEvent event) {
        TaskEventType type = event.getEventType();
        if (type != TaskEventType.create && type != TaskEventType.recreate && type != TaskEventType.timeout) {
            return;
        }
        consumer.consume("ImNotifyEventHandler:task", event.getInstanceId(), () -> {
            List<Long> userIds = extractUserIds(event.getTaskActors());
            if (userIds.isEmpty()) {
                return;
            }
            FlwTask task = event.getTask();
            FlwProcess process = resolveProcessForTask(task);
            String processKey = process != null ? process.getProcessKey() : null;
            String processName = process != null ? process.getProcessName() : null;
            router.dispatch(messageBuilder.buildTaskMessage(type, task, processKey, processName), userIds);
        });
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Async("workflowExecutor")
    public void onInstanceEvent(WorkflowInstanceEvent event) {
        if (!TERMINAL_INSTANCE_EVENTS.contains(event.getEventType())) {
            return;
        }
        consumer.consume("ImNotifyEventHandler:instance", event.getInstanceId(), () -> {
            Long initiatorId = parseUserId(event.getInstance() != null ? event.getInstance().getCreateId() : null);
            if (initiatorId == null) {
                log.info("IM结果通知跳过：发起人不可解析, instanceId={}", event.getInstanceId());
                return;
            }
            FlwProcess process = resolveProcess(event.getProcessId());
            String processKey = process != null ? process.getProcessKey() : null;
            String processName = process != null ? process.getProcessName() : null;
            router.dispatch(messageBuilder.buildInstanceMessage(event.getEventType(), event.getInstance(),
                    processKey, processName), List.of(initiatorId));
        });
    }

    /**
     * 参与者 → 系统用户ID（沿 TaskNotificationListener 口径：仅 user 类型 + Long 可解析）
     */
    private List<Long> extractUserIds(List<FlwTaskActor> taskActors) {
        if (taskActors == null) {
            return List.of();
        }
        return taskActors.stream()
                .filter(actor -> ActorType.user.eq(actor.getActorType()))
                .map(actor -> parseUserId(actor.getActorId()))
                .filter(id -> id != null)
                .distinct()
                .toList();
    }

    private Long parseUserId(String actorId) {
        if (actorId == null) {
            return null;
        }
        try {
            return Long.parseLong(actorId);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 任务事件 → 流程定义（instanceId → instance → processId），失败返回 null 不阻断
     */
    private FlwProcess resolveProcessForTask(FlwTask task) {
        if (task == null || task.getInstanceId() == null) {
            return null;
        }
        try {
            FlwInstance instance = queryService.getInstance(task.getInstanceId());
            return resolveProcess(instance != null ? instance.getProcessId() : null);
        } catch (Exception e) {
            log.warn("IM通知解析实例失败: instanceId={}", task.getInstanceId());
            return null;
        }
    }

    private FlwProcess resolveProcess(Long processId) {
        if (processId == null) {
            return null;
        }
        try {
            return processService.getProcessById(processId);
        } catch (Exception e) {
            log.warn("IM通知解析流程定义失败: processId={}", processId);
            return null;
        }
    }
}
