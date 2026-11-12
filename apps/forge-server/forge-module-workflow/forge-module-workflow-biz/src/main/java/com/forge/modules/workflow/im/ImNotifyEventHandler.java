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
import com.forge.framework.tenant.config.TenantProperties;
import com.forge.framework.tenant.core.context.TenantContextHolder;
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
 * <p>
 * 租户来源（事件优先，fail-closed）：事件发布时刻快照 getTenantId() 优先，缺失退回当前线程上下文；
 * 两者皆无（旧事件/极端无上下文发布）直接 warn 跳过——绝不兜底猜租户，防止跨租户串扰。
 *
 * @author forge-admin
 */
@Slf4j
@Component
public class ImNotifyEventHandler {

    /** 关闸（单租户部署）无上下文时的兜底租户，与 FlowCreatorFactory.currentTenantId() 的回落口径一致 */
    private static final Long DEFAULT_TENANT_ID_WHEN_DISABLED = 1L;

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
    private final TenantProperties tenantProperties;

    public ImNotifyEventHandler(ReliableEventConsumer consumer,
                                ImNotificationRouter router,
                                ImMessageBuilder messageBuilder,
                                ProcessService processService,
                                QueryService queryService,
                                TenantProperties tenantProperties) {
        this.consumer = consumer;
        this.router = router;
        this.messageBuilder = messageBuilder;
        this.processService = processService;
        this.queryService = queryService;
        this.tenantProperties = tenantProperties;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Async("workflowExecutor")
    public void onTaskEvent(WorkflowTaskEvent event) {
        TaskEventType type = event.getEventType();
        if (type != TaskEventType.create && type != TaskEventType.recreate && type != TaskEventType.timeout) {
            return;
        }
        Long tenantId = resolveEventTenantId(event.getTenantId());
        if (tenantId == null) {
            log.warn("IM 推送缺少租户来源，跳过: instanceId={}", event.getInstanceId());
            return;
        }
        consumer.consume("ImNotifyEventHandler:task", event.getInstanceId(), () ->
                runInTenant(tenantId, () -> {
                    List<Long> userIds = extractUserIds(event.getTaskActors());
                    if (userIds.isEmpty()) {
                        return;
                    }
                    FlwTask task = event.getTask();
                    FlwProcess process = resolveProcessForTask(task);
                    String processKey = process != null ? process.getProcessKey() : null;
                    String processName = process != null ? process.getProcessName() : null;
                    router.dispatch(messageBuilder.buildTaskMessage(type, task, processKey, processName), userIds);
                }));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Async("workflowExecutor")
    public void onInstanceEvent(WorkflowInstanceEvent event) {
        if (!TERMINAL_INSTANCE_EVENTS.contains(event.getEventType())) {
            return;
        }
        Long tenantId = resolveEventTenantId(event.getTenantId());
        if (tenantId == null) {
            log.warn("IM 推送缺少租户来源，跳过: instanceId={}", event.getInstanceId());
            return;
        }
        consumer.consume("ImNotifyEventHandler:instance", event.getInstanceId(), () ->
                runInTenant(tenantId, () -> {
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
                }));
    }

    /**
     * 租户来源解析：事件发布时刻快照优先（异步消费线程上下文可能失真），缺失退回当前线程上下文
     */
    private Long resolveEventTenantId(Long eventTenantId) {
        if (eventTenantId != null) {
            return eventTenantId;
        }
        Long contextTenantId = TenantContextHolder.getTenantId();
        if (contextTenantId != null) {
            return contextTenantId;
        }
        // 关闸（tenant.enable=false，上下文链路不装配）单租户部署：保持历史行为按默认租户推送；
        // 开闸则 fail-closed（无来源不发送，绝不猜租户）
        return tenantProperties != null && !Boolean.TRUE.equals(tenantProperties.getEnable())
                ? DEFAULT_TENANT_ID_WHEN_DISABLED : null;
    }

    /**
     * 以事件租户包裹处理段（dispatch 内渠道配置查询自动按租户过滤），
     * 结束恢复线程原上下文（无则清空），避免污染 workflowExecutor 池线程
     */
    private void runInTenant(Long tenantId, Runnable action) {
        Long previous = TenantContextHolder.getTenantId();
        TenantContextHolder.setTenantId(tenantId);
        try {
            action.run();
        } finally {
            if (previous != null) {
                TenantContextHolder.setTenantId(previous);
            } else {
                TenantContextHolder.clear();
            }
        }
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
