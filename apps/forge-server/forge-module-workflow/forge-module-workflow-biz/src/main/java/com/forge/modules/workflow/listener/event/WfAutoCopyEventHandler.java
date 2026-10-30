package com.forge.modules.workflow.listener.event;

import com.aizuda.bpm.engine.core.enums.InstanceEventType;
import com.forge.modules.workflow.service.WfProcessInstanceCopyService;
import com.forge.modules.workflow.framework.event.ReliableEventConsumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.EnumSet;
import java.util.Set;

/**
 * 流程结束自动抄送事件处理器
 *
 * 引擎终态事件（含正常结束/自动通过与驳回/撤销/超时/终止）提交后触发自动抄送：
 * - AFTER_COMMIT 保证终态落库后再生成抄送，抄送失败不影响主流程；
 * - 终止（cancelProcess→terminate）同样以 rejectComplete 事件到达，统一在此处理，
 *   业务侧不再单独直调 autoCopyOnProcessEnd，避免双发。
 *
 * @author forge-admin
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WfAutoCopyEventHandler {

    /** 触发自动抄送的终态事件集 */
    private static final Set<InstanceEventType> TERMINAL_EVENTS = EnumSet.of(
            InstanceEventType.end,
            InstanceEventType.autoComplete,
            InstanceEventType.autoReject,
            InstanceEventType.rejectComplete,
            InstanceEventType.revokeComplete,
            InstanceEventType.timeoutComplete);

    private final WfProcessInstanceCopyService copyService;
    private final ReliableEventConsumer consumer;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onInstanceEvent(WorkflowInstanceEvent event) {
        if (!TERMINAL_EVENTS.contains(event.getEventType())) {
            return;
        }
        consumer.consume("WfAutoCopyEventHandler", event.getInstanceId(), () ->
                copyService.autoCopyOnProcessEnd(
                        String.valueOf(event.getInstanceId()),
                        event.getProcessId() == null ? null : String.valueOf(event.getProcessId()),
                        "流程结束自动抄送（" + event.getEventType() + "）"));
    }
}
