package com.forge.modules.contract.listener;

import com.aizuda.bpm.engine.core.enums.InstanceEventType;
import com.aizuda.bpm.engine.entity.FlwHisInstance;
import com.forge.modules.contract.entity.CtContract;
import com.forge.modules.workflow.listener.event.WorkflowInstanceEvent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 终态事件 → 合同状态映射单测（rejectComplete 按 InstanceState 细分是回归高发点）
 *
 * @author forge-admin
 */
class ContractWorkflowEventHandlerTest {

    private final ContractWorkflowEventHandler handler =
            new ContractWorkflowEventHandler(null, null, null);

    @Test
    void mapApprovedVariants() {
        assertEquals(CtContract.STATUS_APPROVED, handler.mapTerminalStatus(event(InstanceEventType.end, null)));
        assertEquals(CtContract.STATUS_APPROVED, handler.mapTerminalStatus(event(InstanceEventType.autoComplete, null)));
        assertEquals(CtContract.STATUS_APPROVED, handler.mapTerminalStatus(event(InstanceEventType.timeoutComplete, null)));
    }

    @Test
    void mapRejectCompleteByInstanceState() {
        // state=2 审批驳回 → 已驳回
        assertEquals(CtContract.STATUS_REJECTED,
                handler.mapTerminalStatus(event(InstanceEventType.rejectComplete, 2)));
        // state=5 发起人取消（terminate）→ 已撤销
        assertEquals(CtContract.STATUS_CANCELED,
                handler.mapTerminalStatus(event(InstanceEventType.rejectComplete, 5)));
    }

    @Test
    void mapOtherTerminalsAndIntermediateIgnored() {
        assertEquals(CtContract.STATUS_REJECTED, handler.mapTerminalStatus(event(InstanceEventType.autoReject, null)));
        assertEquals(CtContract.STATUS_CANCELED, handler.mapTerminalStatus(event(InstanceEventType.revokeComplete, null)));
        assertNull(handler.mapTerminalStatus(event(InstanceEventType.start, null)), "中间态不回写");
    }

    private WorkflowInstanceEvent event(InstanceEventType type, Integer instanceState) {
        FlwHisInstance instance = new FlwHisInstance();
        if (instanceState != null) {
            instance.setInstanceState(instanceState);
        }
        return new WorkflowInstanceEvent(this, type, instance, null, null, 1L);
    }
}
