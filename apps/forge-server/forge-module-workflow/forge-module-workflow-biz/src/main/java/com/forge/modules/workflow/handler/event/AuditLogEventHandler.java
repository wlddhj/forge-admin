package com.forge.modules.workflow.handler.event;

import com.aizuda.bpm.engine.core.enums.InstanceEventType;
import com.aizuda.bpm.engine.core.enums.TaskEventType;
import com.forge.modules.workflow.entity.WfEventAudit;
import com.forge.modules.workflow.listener.event.WorkflowInstanceEvent;
import com.forge.modules.workflow.listener.event.WorkflowTaskEvent;
import com.forge.modules.workflow.mapper.WfEventAuditMapper;
import com.forge.modules.workflow.framework.event.ReliableEventConsumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 工作流事件审计处理器
 * 异步消费引擎 Spring 事件并写入 wf_event_audit 审计表
 *
 * @author forge-admin
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuditLogEventHandler {

    private final WfEventAuditMapper auditMapper;
    private final ReliableEventConsumer consumer;

    @Async
    @EventListener
    public void handleTaskEvent(WorkflowTaskEvent event) {
        consumer.consume("AuditLogEventHandler:task", event.getInstanceId(), () -> {
            WfEventAudit audit = new WfEventAudit();
            audit.setEventType("task:" + event.getEventType().name());
            audit.setInstanceId(event.getTask() != null ? event.getTask().getInstanceId() : null);
            audit.setTaskId(event.getTaskId());
            audit.setTaskName(event.getTaskName());
            audit.setOperatorId(event.getOperatorId());
            audit.setOperatorName(event.getOperatorName());
            audit.setSummary(String.format("任务事件[%s] taskId=%s, taskName=%s, operator=%s",
                    event.getEventType().name(), event.getTaskId(), event.getTaskName(), event.getOperatorName()));
            audit.setCreateTime(LocalDateTime.now());
            auditMapper.insert(audit);
        });
    }

    @Async
    @EventListener
    public void handleInstanceEvent(WorkflowInstanceEvent event) {
        consumer.consume("AuditLogEventHandler:instance", event.getInstanceId(), () -> {
            WfEventAudit audit = new WfEventAudit();
            audit.setEventType("instance:" + event.getEventType().name());
            audit.setInstanceId(event.getInstanceId());
            audit.setTaskId(null);
            audit.setTaskName(null);
            audit.setOperatorId(event.getOperatorId());
            audit.setOperatorName(event.getOperatorName());
            audit.setSummary(String.format("实例事件[%s] instanceId=%s, processId=%s, operator=%s",
                    event.getEventType().name(), event.getInstanceId(), event.getProcessId(), event.getOperatorName()));
            audit.setCreateTime(LocalDateTime.now());
            auditMapper.insert(audit);
        });
    }
}
