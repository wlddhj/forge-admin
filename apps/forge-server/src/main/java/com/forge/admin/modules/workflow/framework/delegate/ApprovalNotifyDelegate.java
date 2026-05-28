package com.forge.admin.modules.workflow.framework.delegate;

import com.forge.admin.common.websocket.NotificationMessage;
import com.forge.admin.common.websocket.NotificationService;
import com.forge.admin.modules.workflow.identity.FlowableIdentityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

/**
 * 审批结果通知委托
 * 审批完成后自动通知流程发起人
 *
 * BPMN 配置: flowable:delegateExpression="${approvalNotifyDelegate}"
 * 使用场景: 排他网关之后，审批通过/驳回分支上各放一个，分别设置 approved 变量
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
