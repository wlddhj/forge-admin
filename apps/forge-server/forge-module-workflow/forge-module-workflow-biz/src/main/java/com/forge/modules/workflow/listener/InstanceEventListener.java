package com.forge.modules.workflow.listener;

import com.aizuda.bpm.engine.core.FlowCreator;
import com.aizuda.bpm.engine.core.enums.InstanceEventType;
import com.aizuda.bpm.engine.entity.FlwHisInstance;
import com.aizuda.bpm.engine.model.NodeModel;
import com.aizuda.bpm.spring.event.EventInstanceListener;
import com.aizuda.bpm.spring.event.InstanceEvent;
import com.forge.modules.workflow.listener.event.WorkflowInstanceEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

/**
 * 流程实例事件监听器 - 继承 EventInstanceListener
 *
 * 继承 FlowLong 的 EventInstanceListener，保留事件推送功能，
 * 同时添加自定义业务逻辑（发布 WorkflowInstanceEvent）
 *
 * 支持的事件类型：
 * - start: 流程发起
 * - suspend: 流程暂停
 * - forceComplete: 强制完成
 * - rejectComplete: 驳回完成
 * - revokeComplete: 撤销完成
 * - timeoutComplete: 超时完成
 * - autoComplete: 自动完成
 * - autoReject: 自动驳回
 * - end: 流程结束
 *
 * @author forge-admin
 */
@Slf4j
@Component("instanceListener")
public class InstanceEventListener extends EventInstanceListener {

    private final ApplicationEventPublisher workflowEventPublisher;

    public InstanceEventListener(ApplicationEventPublisher eventPublisher) {
        super(eventPublisher);
        this.workflowEventPublisher = eventPublisher;
    }

    @Override
    public boolean notify(InstanceEventType eventType, Supplier<FlwHisInstance> supplier,
                          NodeModel nodeModel, FlowCreator flowCreator) {
        // 先调用父类方法，发布 FlowLong 的 InstanceEvent
        super.notify(eventType, supplier, nodeModel, flowCreator);

        FlwHisInstance instance = supplier.get();
        if (instance == null) {
            log.warn("流程实例事件监听: 实例为空, eventType={}", eventType);
            return false;
        }

        log.info("流程实例事件: type={}, instanceId={}, processId={}, operator={}",
                eventType.name(), instance.getId(), instance.getProcessId(),
                flowCreator != null ? flowCreator.getCreateBy() : "unknown");

        // 发布自定义的 WorkflowInstanceEvent 供其他组件处理
        WorkflowInstanceEvent event = new WorkflowInstanceEvent(
                this, eventType, instance, nodeModel, flowCreator);
        workflowEventPublisher.publishEvent(event);

        // 返回 false 表示不干预流程执行
        return false;
    }
}