package com.forge.admin.modules.workflow.framework.listener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.common.engine.api.delegate.event.FlowableEvent;
import org.flowable.common.engine.api.delegate.event.FlowableEventListener;
import org.flowable.common.engine.impl.event.FlowableEntityEventImpl;
import org.flowable.task.api.Task;
import org.flowable.task.service.delegate.DelegateTask;
import org.springframework.stereotype.Component;

/**
 * 全局任务创建事件监听器
 * 拦截所有任务创建事件，调用候选人分配逻辑
 *
 * @author forge-admin
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GlobalTaskCreatedEventListener implements FlowableEventListener {

    private final BpmTaskCandidateListener bpmTaskCandidateListener;

    @Override
    public void onEvent(FlowableEvent event) {
        // 处理任务创建事件
        if (event instanceof FlowableEntityEventImpl) {
            FlowableEntityEventImpl entityEvent = (FlowableEntityEventImpl) event;
            Object entity = entityEvent.getEntity();
            if (entity instanceof DelegateTask) {
                bpmTaskCandidateListener.assignCandidates((DelegateTask) entity);
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