package com.forge.modules.workflow.framework.ai;

import com.forge.framework.tenant.core.context.TenantContextHolder;
import com.forge.modules.workflow.framework.event.ReliableEventConsumer;
import com.forge.modules.workflow.listener.event.WorkflowAiApprovalEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * AI审批事件消费者
 *
 * 任务创建事务提交后在 workflowExecutor 线程执行 AI 审批：
 * - AFTER_COMMIT 保证主流程事务先落库，AI 失败不影响任务创建；
 * - fallbackExecution=true 兼容无事务上下文的发布路径；
 * - 消费全程 try-catch，异常只记日志不传播。
 *
 * @author forge-admin
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiApprovalEventConsumer {

    private final AiApprovalExecutor aiApprovalExecutor;
    private final ReliableEventConsumer consumer;

    @Async("workflowExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onAiApprovalEvent(WorkflowAiApprovalEvent event) {
        // 租户上下文随重试单元重建（重试跑在独立调度线程，ThreadLocal 不跨线程）
        consumer.consume("AiApprovalEventConsumer:" + event.getTaskId(), null, () -> {
            try {
                if (event.getTenantId() != null) {
                    TenantContextHolder.setTenantId(event.getTenantId());
                }
                aiApprovalExecutor.executeForEvent(event);
            } finally {
                TenantContextHolder.clear();
            }
        });
    }
}
