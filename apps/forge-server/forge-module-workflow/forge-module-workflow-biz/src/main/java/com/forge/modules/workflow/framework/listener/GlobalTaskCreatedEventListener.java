package com.forge.modules.workflow.framework.listener;

import com.forge.modules.workflow.listener.TaskNotificationListener;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.common.engine.api.delegate.event.FlowableEvent;
import org.flowable.common.engine.api.delegate.event.FlowableEventListener;
import org.flowable.common.engine.impl.event.FlowableEntityEventImpl;
import org.flowable.task.service.delegate.DelegateTask;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/**
 * 全局任务创建事件监听器
 * 拦截所有任务创建事件，依次调用候选人分配和通知逻辑
 *
 * @author forge-admin
 */
@Slf4j
@Component
public class GlobalTaskCreatedEventListener implements FlowableEventListener {

    private final BpmTaskCandidateListener bpmTaskCandidateListener;
    private TaskNotificationListener taskNotificationListener;

    public GlobalTaskCreatedEventListener(BpmTaskCandidateListener bpmTaskCandidateListener) {
        this.bpmTaskCandidateListener = bpmTaskCandidateListener;
    }

    @Autowired
    @Lazy
    public void setTaskNotificationListener(TaskNotificationListener taskNotificationListener) {
        this.taskNotificationListener = taskNotificationListener;
    }

    @Override
    public void onEvent(FlowableEvent event) {
        if (event instanceof FlowableEntityEventImpl) {
            FlowableEntityEventImpl entityEvent = (FlowableEntityEventImpl) event;
            Object entity = entityEvent.getEntity();
            if (entity instanceof DelegateTask) {
                DelegateTask delegateTask = (DelegateTask) entity;
                // 先分配候选人
                bpmTaskCandidateListener.notify(delegateTask);
                // 再发送通知
                taskNotificationListener.notify(delegateTask);
            }
        }
    }

    @Override
    public boolean isFireOnTransactionLifecycleEvent() {
        return false;
    }

    @Override
    public String getOnTransaction() {
        return null;
    }

    @Override
    public boolean isFailOnException() {
        return false;
    }
}