package com.forge.admin.modules.workflow.framework.listener;

import com.forge.admin.modules.workflow.service.WfProcessInstanceCopyService;
import lombok.extern.slf4j.Slf4j;
import org.flowable.common.engine.api.delegate.event.FlowableEvent;
import org.flowable.common.engine.api.delegate.event.FlowableEventListener;
import org.flowable.common.engine.impl.event.FlowableEntityEventImpl;
import org.flowable.engine.impl.persistence.entity.ExecutionEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/**
 * 全局流程完成事件监听器
 * 流程正常结束时自动抄送给指定人员
 */
@Slf4j
@Component
public class GlobalProcessCompletedEventListener implements FlowableEventListener {

    private WfProcessInstanceCopyService copyService;

    @Autowired
    @Lazy
    public void setCopyService(WfProcessInstanceCopyService copyService) {
        this.copyService = copyService;
    }

    @Override
    public void onEvent(FlowableEvent event) {
        try {
            if (event instanceof FlowableEntityEventImpl entityEvent) {
                Object entity = entityEvent.getEntity();
                if (entity instanceof ExecutionEntity execution) {
                    String processInstanceId = execution.getProcessInstanceId();
                    String processDefinitionId = execution.getProcessDefinitionId();
                    copyService.autoCopyOnProcessEnd(processInstanceId, processDefinitionId, "流程结束自动抄送");
                }
            }
        } catch (Exception e) {
            log.error("自动抄送处理异常", e);
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
