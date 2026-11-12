package com.forge.modules.workflow.callback;

import com.aizuda.bpm.engine.ProcessService;
import com.aizuda.bpm.engine.QueryService;
import com.aizuda.bpm.engine.core.enums.InstanceEventType;
import com.aizuda.bpm.engine.core.enums.TaskEventType;
import com.aizuda.bpm.engine.entity.FlwInstance;
import com.aizuda.bpm.engine.entity.FlwProcess;
import com.aizuda.bpm.engine.entity.FlwTask;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.forge.framework.tenant.config.TenantProperties;
import com.forge.framework.tenant.core.context.TenantContextHolder;
import com.forge.modules.workflow.entity.WfBusinessCallback;
import com.forge.modules.workflow.framework.event.ReliableEventConsumer;
import com.forge.modules.workflow.listener.event.WorkflowInstanceEvent;
import com.forge.modules.workflow.listener.event.WorkflowTaskEvent;
import com.forge.modules.workflow.mapper.WfBusinessCallbackMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * 业务回调事件监听器：消费工作流 Spring 事件（事务提交后异步），
 * 匹配回调配置并投递。任何异常自行吞掉，绝不影响主流程。
 * <p>
 * 投递租户以事件携带值为准（缺省回退上下文），处理段包裹租户上下文，
 * 使回调查询与投递记录按租户隔离；无租户来源则告警跳过（fail-closed）。
 *
 * @author forge-admin
 */
@Slf4j
@Component
public class BusinessCallbackEventListener {

    private final ReliableEventConsumer consumer;
    /** 关闸（单租户部署）无上下文时的兜底租户，与 FlowCreatorFactory.currentTenantId() 的回落口径一致 */
    private static final Long DEFAULT_TENANT_ID_WHEN_DISABLED = 1L;
    /**
     * 引擎实例终态集合 → 契约 instanceFinished
     */
    private static final Set<InstanceEventType> TERMINAL_INSTANCE_EVENTS = EnumSet.of(
            InstanceEventType.forceComplete, InstanceEventType.rejectComplete,
            InstanceEventType.revokeComplete, InstanceEventType.timeoutComplete,
            InstanceEventType.autoComplete, InstanceEventType.autoReject, InstanceEventType.end);

    private final WfBusinessCallbackMapper callbackMapper;
    private final CallbackPayloadBuilder payloadBuilder;
    private final CallbackDispatcher dispatcher;
    private final ProcessService processService;
    private final QueryService queryService;
    private final TenantProperties tenantProperties;

    public BusinessCallbackEventListener(WfBusinessCallbackMapper callbackMapper,
                                         CallbackPayloadBuilder payloadBuilder,
                                         CallbackDispatcher dispatcher,
                                         ProcessService processService,
                                         QueryService queryService,
                                         ReliableEventConsumer consumer,
                                         TenantProperties tenantProperties) {
        this.consumer = consumer;
        this.callbackMapper = callbackMapper;
        this.payloadBuilder = payloadBuilder;
        this.dispatcher = dispatcher;
        this.processService = processService;
        this.queryService = queryService;
        this.tenantProperties = tenantProperties;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Async("workflowExecutor")
    public void onTaskEvent(WorkflowTaskEvent event) {
        Long tenantId = resolveTenantId("task", event.getTenantId(), event.getInstanceId());
        if (tenantId == null) {
            return;
        }
        consumer.consume("BusinessCallbackEventListener:task", event.getInstanceId(), () ->
                runWithTenant(tenantId, () -> {
                    CallbackEventType eventType = mapTaskEvent(event.getEventType());
                    if (eventType == null) {
                        return;
                    }
                    FlwTask task = event.getTask();
                    FlwInstance instance = task != null && task.getInstanceId() != null
                            ? queryService.getInstance(task.getInstanceId()) : null;
                    FlwProcess process = resolveProcess(instance != null ? instance.getProcessId() : null);
                    String processKey = process != null ? process.getProcessKey() : null;

                    for (WfBusinessCallback callback : enabledCallbacks(eventType, processKey)) {
                        String payload = payloadBuilder.build(eventType, event, processKey);
                        dispatcher.deliver(callback, eventType.name(), processKey,
                                null, task != null ? task.getInstanceId() : null,
                                task != null ? task.getId() : null,
                                task != null ? task.getTaskKey() : null, payload);
                    }
                }));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Async("workflowExecutor")
    public void onInstanceEvent(WorkflowInstanceEvent event) {
        if (!TERMINAL_INSTANCE_EVENTS.contains(event.getEventType())) {
            return;
        }
        Long tenantId = resolveTenantId("instance", event.getTenantId(), event.getInstanceId());
        if (tenantId == null) {
            return;
        }
        consumer.consume("BusinessCallbackEventListener:instance", event.getInstanceId(), () ->
                runWithTenant(tenantId, () -> {
                    FlwProcess process = resolveProcess(event.getProcessId());
                    String processKey = process != null ? process.getProcessKey() : null;
                    String processName = process != null ? process.getProcessName() : null;

                    for (WfBusinessCallback callback : enabledCallbacks(CallbackEventType.instanceFinished, processKey)) {
                        boolean includeVariables = callback.getIncludeVariables() != null && callback.getIncludeVariables() == 1;
                        String payload = payloadBuilder.build(event, processKey, processName, includeVariables);
                        dispatcher.deliver(callback, CallbackEventType.instanceFinished.name(), processKey,
                                event.getBusinessKey(), event.getInstanceId(), null, null, payload);
                    }
                }));
    }

    /**
     * 解析投递租户：以事件发布时刻携带值为准，缺省回退当前上下文；
     * 仍无租户则告警并跳过（fail-closed，绝不猜租户投递）
     */
    private Long resolveTenantId(String source, Long eventTenantId, Long instanceId) {
        if (eventTenantId != null) {
            return eventTenantId;
        }
        Long contextTenantId = TenantContextHolder.getTenantId();
        if (contextTenantId != null) {
            return contextTenantId;
        }
        // 关闸（tenant.enable=false，上下文链路不装配）单租户部署：保持历史行为按默认租户投递；
        // 开闸则 fail-closed（无来源不投递，绝不猜租户）——与 ImNotifyEventHandler 口径一致
        if (tenantProperties != null && !Boolean.TRUE.equals(tenantProperties.getEnable())) {
            return DEFAULT_TENANT_ID_WHEN_DISABLED;
        }
        log.warn("回调事件缺少租户来源，跳过投递: source={}, instanceId={}", source, instanceId);
        return null;
    }

    /**
     * 以指定租户执行处理段：enabledCallbacks 查询与投递记录落库
     * 由租户拦截器按上下文自动过滤/注入 tenant_id 列；结束后恢复原上下文防池线程污染
     */
    private void runWithTenant(Long tenantId, Runnable action) {
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

    private CallbackEventType mapTaskEvent(TaskEventType type) {
        if (type == null) return null;
        return switch (type) {
            case create, recreate -> CallbackEventType.taskCreated;
            case complete -> CallbackEventType.taskCompleted;
            default -> null;
        };
    }

    private List<WfBusinessCallback> enabledCallbacks(CallbackEventType eventType, String processKey) {
        List<WfBusinessCallback> all = callbackMapper.selectList(new LambdaQueryWrapper<WfBusinessCallback>()
                .eq(WfBusinessCallback::getEnabled, 1));
        String eventKey = "," + eventType.name() + ",";
        return all.stream()
                .filter(cb -> ("," + cb.getEvents() + ",").contains(eventKey))
                .filter(cb -> cb.getProcessKey() == null || cb.getProcessKey().isEmpty()
                        || cb.getProcessKey().equals(processKey))
                .toList();
    }

    private FlwProcess resolveProcess(Long processId) {
        if (processId == null) return null;
        try {
            return processService.getProcessById(processId);
        } catch (Exception e) {
            log.warn("回调解析流程定义失败: processId={}", processId);
            return null;
        }
    }
}
