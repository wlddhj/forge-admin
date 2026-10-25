package com.forge.modules.workflow.listener.event;

import com.forge.modules.workflow.dto.ai.AiApprovalConfig;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * AI 审批触发事件 - 任务创建事务提交后异步执行 AI 审批
 *
 * 主事务内只发布本事件（CompositeTaskListener），LLM 调用与自动审批动作
 * 由 {@code AiApprovalEventConsumer} 在 AFTER_COMMIT 阶段经 workflowExecutor 执行，
 * 避免在引擎流转事务中同步调用 LLM 并重入引擎。
 *
 * @author forge-admin
 */
@Getter
public class WorkflowAiApprovalEvent extends ApplicationEvent {

    /** 触发 AI 审批的任务 ID */
    private final Long taskId;

    /** 节点 AI 审批配置快照（发布时从 NodeModel.extendConfig 解析） */
    private final AiApprovalConfig aiConfig;

    /** 操作人 ID（任务创建链路的 FlowCreator.createId） */
    private final String operatorId;

    /** 操作人名称 */
    private final String operatorName;

    /** 租户快照（异步线程回填 TenantContextHolder） */
    private final Long tenantId;

    public WorkflowAiApprovalEvent(Object source, Long taskId, AiApprovalConfig aiConfig,
                                   String operatorId, String operatorName, Long tenantId) {
        super(source);
        this.taskId = taskId;
        this.aiConfig = aiConfig;
        this.operatorId = operatorId;
        this.operatorName = operatorName;
        this.tenantId = tenantId;
    }
}
