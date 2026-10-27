package com.forge.modules.workflow.callback;

import com.aizuda.bpm.engine.ProcessService;
import com.aizuda.bpm.engine.QueryService;
import com.aizuda.bpm.engine.core.enums.InstanceEventType;
import com.aizuda.bpm.engine.core.enums.TaskEventType;
import com.aizuda.bpm.engine.entity.FlwInstance;
import com.aizuda.bpm.engine.entity.FlwProcess;
import com.aizuda.bpm.engine.entity.FlwTask;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.forge.modules.workflow.entity.WfBusinessCallback;
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
 *
 * @author forge-admin
 */
@Slf4j
@Component
public class BusinessCallbackEventListener {

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

    public BusinessCallbackEventListener(WfBusinessCallbackMapper callbackMapper,
                                         CallbackPayloadBuilder payloadBuilder,
                                         CallbackDispatcher dispatcher,
                                         ProcessService processService,
                                         QueryService queryService) {
        this.callbackMapper = callbackMapper;
        this.payloadBuilder = payloadBuilder;
        this.dispatcher = dispatcher;
        this.processService = processService;
        this.queryService = queryService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Async("workflowExecutor")
    public void onTaskEvent(WorkflowTaskEvent event) {
        try {
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
        } catch (Exception e) {
            log.error("业务回调任务事件处理异常: type={}", event.getEventType(), e);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Async("workflowExecutor")
    public void onInstanceEvent(WorkflowInstanceEvent event) {
        try {
            if (!TERMINAL_INSTANCE_EVENTS.contains(event.getEventType())) {
                return;
            }
            FlwProcess process = resolveProcess(event.getProcessId());
            String processKey = process != null ? process.getProcessKey() : null;
            String processName = process != null ? process.getProcessName() : null;

            for (WfBusinessCallback callback : enabledCallbacks(CallbackEventType.instanceFinished, processKey)) {
                boolean includeVariables = callback.getIncludeVariables() != null && callback.getIncludeVariables() == 1;
                String payload = payloadBuilder.build(event, processKey, processName, includeVariables);
                dispatcher.deliver(callback, CallbackEventType.instanceFinished.name(), processKey,
                        event.getBusinessKey(), event.getInstanceId(), null, null, payload);
            }
        } catch (Exception e) {
            log.error("业务回调实例事件处理异常: type={}", event.getEventType(), e);
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
