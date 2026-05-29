package com.forge.modules.workflow.framework.delegate;

import com.forge.framework.web.websocket.NotificationMessage;
import com.forge.framework.web.websocket.NotificationService;
import com.forge.modules.workflow.identity.FlowableIdentityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

/**
 * 审批结果通知委托
 * 审批通过后自动通知流程发起人
 *
 * BPMN 配置: flowable:delegateExpression="${approvalNotifyDelegate}"
 * 使用场景: 用户审批节点之后，审批通过时 WfTaskServiceImpl 已设置 approved=true，
 *          驳回时流程直接终止不会走到此节点
 *
 * 流程示例: 开始 → 用户任务(审批) → [本节点] → 结束
 */
@Slf4j
@Component("approvalNotifyDelegate")
@RequiredArgsConstructor
public class ApprovalNotifyDelegate implements JavaDelegate {

    private final NotificationService notificationService;
    private final FlowableIdentityService identityService;

    @Override
    public void execute(DelegateExecution execution) {
        String processNo = (String) execution.getVariable("processNo");
        Object approvedObj = execution.getVariable("approved");
        boolean approved = Boolean.TRUE.equals(approvedObj);

        Object initiatorObj = execution.getVariable("initiator");
        if (initiatorObj == null) {
            log.warn("流程 {} 未找到发起人变量 initiator", processNo);
            return;
        }

        Long initiatorId = Long.valueOf(initiatorObj.toString());
        String initiatorName = identityService.getUserName(initiatorId);
        String title = approved ? "流程审批通过" : "流程审批驳回";
        String content = String.format("您发起的流程[%s]已被%s", processNo, approved ? "通过" : "驳回");

        notificationService.sendToUser(initiatorId, NotificationMessage.workflow(title, content, null));

        log.info("审批结果通知已发送: processNo={}, approved={}, initiator={}", processNo, approved, initiatorName);
    }
}
