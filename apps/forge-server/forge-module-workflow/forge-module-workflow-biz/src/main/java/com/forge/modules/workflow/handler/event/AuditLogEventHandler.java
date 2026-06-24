package com.forge.modules.workflow.handler.event;

import com.aizuda.bpm.engine.core.enums.InstanceEventType;
import com.aizuda.bpm.engine.core.enums.TaskEventType;
import com.forge.modules.workflow.listener.event.WorkflowInstanceEvent;
import com.forge.modules.workflow.listener.event.WorkflowTaskEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 工作流审计日志事件处理器
 * 记录所有工作流操作的审计日志
 *
 * @author forge-admin
 */
@Slf4j
@Component
public class AuditLogEventHandler {

    /**
     * 处理任务事件 - 记录审计日志
     */
    @Async
    @EventListener
    public void handleTaskEvent(WorkflowTaskEvent event) {
        TaskEventType eventType = event.getEventType();
        Long taskId = event.getTaskId();
        String taskName = event.getTaskName();
        String operatorId = event.getOperatorId();
        String operatorName = event.getOperatorName();

        log.info("审计日志 - 任务事件: type={}, taskId={}, taskName={}, operatorId={}, operatorName={}",
                eventType.name(), taskId, taskName, operatorId, operatorName);

        // TODO: 将审计日志持久化到数据库
        // 可以集成现有的 OperationLog 机制
        recordAuditLog(event);
    }

    /**
     * 处理流程实例事件 - 记录审计日志
     */
    @Async
    @EventListener
    public void handleInstanceEvent(WorkflowInstanceEvent event) {
        InstanceEventType eventType = event.getEventType();
        Long instanceId = event.getInstanceId();
        Long processId = event.getProcessId();
        String operatorId = event.getOperatorId();
        String operatorName = event.getOperatorName();

        log.info("审计日志 - 实例事件: type={}, instanceId={}, processId={}, operatorId={}, operatorName={}",
                eventType.name(), instanceId, processId, operatorId, operatorName);

        // TODO: 将审计日志持久化到数据库
        recordInstanceAuditLog(event);
    }

    /**
     * 记录任务审计日志
     */
    private void recordAuditLog(WorkflowTaskEvent event) {
        // 根据事件类型记录不同的审计信息
        switch (event.getEventType()) {
            case create:
                logOperation("创建任务", event.getTaskName(), event.getOperatorId());
                break;
            case complete:
                logOperation("完成任务", event.getTaskName(), event.getOperatorId());
                break;
            case reject:
                logOperation("驳回任务", event.getTaskName(), event.getOperatorId());
                break;
            case transfer:
                logOperation("转办任务", event.getTaskName(), event.getOperatorId());
                break;
            case delegate:
                logOperation("委派任务", event.getTaskName(), event.getOperatorId());
                break;
            case withdraw:
                logOperation("撤回任务", event.getTaskName(), event.getOperatorId());
                break;
            case timeout:
                logOperation("任务超时", event.getTaskName(), "SYSTEM");
                break;
            case autoComplete:
                logOperation("自动通过", event.getTaskName(), "SYSTEM");
                break;
            case autoReject:
                logOperation("自动驳回", event.getTaskName(), "SYSTEM");
                break;
            default:
                log.debug("未记录审计日志的事件类型: {}", event.getEventType());
        }
    }

    /**
     * 记录实例审计日志
     */
    private void recordInstanceAuditLog(WorkflowInstanceEvent event) {
        String processIdStr = event.getProcessId() != null ? event.getProcessId().toString() : "unknown";
        switch (event.getEventType()) {
            case start:
                logOperation("发起流程", processIdStr, event.getOperatorId());
                break;
            case suspend:
                logOperation("暂停流程", processIdStr, event.getOperatorId());
                break;
            case end:
                logOperation("结束流程", processIdStr, event.getOperatorId());
                break;
            case timeoutComplete:
                logOperation("流程超时结束", processIdStr, "SYSTEM");
                break;
            case autoComplete:
                logOperation("流程自动完成", processIdStr, "SYSTEM");
                break;
            case autoReject:
                logOperation("流程自动驳回", processIdStr, "SYSTEM");
                break;
            default:
                log.debug("未记录审计日志的事件类型: {}", event.getEventType());
        }
    }

    /**
     * 记录操作日志（简化版）
     * 实际应集成 OperationLogAspect
     */
    private void logOperation(String operation, String target, String operatorId) {
        log.info("工作流审计: operation={}, target={}, operator={}", operation, target, operatorId);
    }
}