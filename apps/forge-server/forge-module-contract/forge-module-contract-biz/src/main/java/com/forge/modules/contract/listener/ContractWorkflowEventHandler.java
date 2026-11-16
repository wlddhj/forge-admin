package com.forge.modules.contract.listener;

import com.aizuda.bpm.engine.entity.FlwHisInstance;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.forge.framework.tenant.core.context.TenantContextHolder;
import com.forge.modules.contract.entity.CtContract;
import com.forge.modules.contract.mapper.CtContractMapper;
import com.forge.modules.contract.service.ContractService;
import com.forge.modules.workflow.framework.event.ReliableEventConsumer;
import com.forge.modules.workflow.listener.event.WorkflowInstanceEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * 工作流实例事件 → 合同状态回写（形态A，范式照抄 forge-module-demo ProjectWorkflowEventHandler）。
 * <p>
 * 通过：finalizeApproved（快照+水印 PDF+编号 → CAS 落「已通过」）；
 * 驳回/撤销：CAS 落 3/4（可重新编辑再提交）。
 *
 * @author forge-admin
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ContractWorkflowEventHandler {

    private static final String BUSINESS_KEY_PREFIX = "contract:";

    private final CtContractMapper contractMapper;
    private final ReliableEventConsumer consumer;
    private final ContractService contractService;

    @Async
    @EventListener
    public void onInstanceEvent(WorkflowInstanceEvent event) {
        String businessKey = event.getBusinessKey();
        if (businessKey == null || !businessKey.startsWith(BUSINESS_KEY_PREFIX)) {
            return;
        }
        Long contractId;
        try {
            contractId = Long.parseLong(businessKey.substring(BUSINESS_KEY_PREFIX.length()));
        } catch (NumberFormatException e) {
            log.warn("合同事件 businessKey 无法解析业务ID: {}", businessKey);
            return;
        }

        Integer targetStatus = mapTerminalStatus(event);
        if (targetStatus == null) {
            return; // 中间态不回写业务状态
        }

        consumer.consume("ContractWorkflowEventHandler", event.getInstanceId(), () -> {
            TenantContextHolder.setTenantId(resolveTenantId(event.getInstance()));
            try {
                CtContract contract = contractMapper.selectById(contractId);
                if (contract == null) {
                    log.warn("合同事件回写落空（记录不存在）: contractId={}, type={}", contractId, event.getEventType());
                    return;
                }
                // ① 绑定实例校验：须与合同绑定的实例一致才允许回写
                if (!Objects.equals(contract.getProcessInstanceId(), event.getInstanceId())) {
                    log.warn("合同事件实例ID与绑定实例不匹配，跳过回写: contractId={}, boundInstanceId={}, eventInstanceId={}",
                            contractId, contract.getProcessInstanceId(), event.getInstanceId());
                    return;
                }
                if (Objects.equals(contract.getStatus(), targetStatus)) {
                    return; // 事件重复投递，同值幂等跳过
                }
                if (targetStatus == CtContract.STATUS_APPROVED) {
                    // 通过：定稿编排（渲染快照+水印 PDF 归档+编号，内部 CAS 1→2）
                    contractService.finalizeApproved(contract);
                    return;
                }
                // 驳回/撤销：CAS 仅审批中可写终态
                int updated = contractMapper.update(new LambdaUpdateWrapper<CtContract>()
                        .eq(CtContract::getId, contractId)
                        .eq(CtContract::getStatus, CtContract.STATUS_APPROVING)
                        .set(CtContract::getStatus, targetStatus));
                if (updated == 0) {
                    log.info("合同状态已非审批中，跳过回写: contractId={}, currentStatus={}, targetStatus={}",
                            contractId, contract.getStatus(), targetStatus);
                    return;
                }
                log.info("合同状态回写: contractId={}, {} -> {}（instanceId={}）",
                        contractId, contract.getStatus(), targetStatus, event.getInstanceId());
            } finally {
                TenantContextHolder.clear();
            }
        });
    }

    /**
     * 实例终态事件 → 合同状态映射（以引擎实际触发为准；rejectComplete 按 InstanceState 细分，
     * state=5 为发起人取消 → 已撤销）
     */
    Integer mapTerminalStatus(WorkflowInstanceEvent event) {
        return switch (event.getEventType()) {
            case end, autoComplete, timeoutComplete -> CtContract.STATUS_APPROVED;
            case rejectComplete -> isTerminated(event.getInstance())
                    ? CtContract.STATUS_CANCELED
                    : CtContract.STATUS_REJECTED;
            case autoReject -> CtContract.STATUS_REJECTED;
            case revokeComplete -> CtContract.STATUS_CANCELED;
            default -> null;
        };
    }

    boolean isTerminated(FlwHisInstance instance) {
        return instance != null && Integer.valueOf(5).equals(instance.getInstanceState());
    }

    private Long resolveTenantId(FlwHisInstance instance) {
        if (instance == null || instance.getTenantId() == null || instance.getTenantId().isBlank()) {
            return 1L;
        }
        try {
            return Long.parseLong(instance.getTenantId());
        } catch (NumberFormatException e) {
            return 1L;
        }
    }
}
