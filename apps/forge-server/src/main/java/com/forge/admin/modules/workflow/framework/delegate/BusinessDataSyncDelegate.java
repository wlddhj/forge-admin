package com.forge.admin.modules.workflow.framework.delegate;

import com.forge.admin.common.websocket.NotificationMessage;
import com.forge.admin.common.websocket.NotificationService;
import com.forge.admin.modules.workflow.identity.FlowableIdentityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 业务数据状态同步委托
 * 审批通过后将审批结果写入 Redis，并通知发起人
 *
 * BPMN 配置: flowable:delegateExpression="${businessDataSyncDelegate}"
 * 使用场景: 审批通过分支上，用于同步业务系统状态
 */
@Slf4j
@Component("businessDataSyncDelegate")
@RequiredArgsConstructor
public class BusinessDataSyncDelegate implements JavaDelegate {

    private final StringRedisTemplate redisTemplate;
    private final NotificationService notificationService;
    private final FlowableIdentityService identityService;

    private static final String REDIS_KEY_PREFIX = "wf:approval_result:";

    @Override
    public void execute(DelegateExecution execution) {
        String processNo = (String) execution.getVariable("processNo");
        String businessKey = execution.getProcessInstanceBusinessKey();
        Object approvedObj = execution.getVariable("approved");
        boolean approved = Boolean.TRUE.equals(approvedObj);

        String processInstanceId = execution.getProcessInstanceId();
        String processDefinitionName = execution.getProcessDefinitionId();

        // 将审批结果写入 Redis 缓存，有效期 7 天
        Map<String, String> resultData = new HashMap<>();
        resultData.put("processNo", processNo != null ? processNo : "");
        resultData.put("businessKey", businessKey != null ? businessKey : "");
        resultData.put("approved", String.valueOf(approved));
        resultData.put("processInstanceId", processInstanceId);
        resultData.put("syncTime", String.valueOf(System.currentTimeMillis()));

        String redisKey = REDIS_KEY_PREFIX + processNo;
        redisTemplate.opsForHash().putAll(redisKey, resultData);
        redisTemplate.expire(redisKey, 7, TimeUnit.DAYS);

        // 设置输出变量，后续节点可读取
        execution.setVariable("syncResult", "SUCCESS");
        execution.setVariable("syncTime", System.currentTimeMillis());

        // 通知发起人
        Object initiatorObj = execution.getVariable("initiator");
        if (initiatorObj != null) {
            Long initiatorId = Long.valueOf(initiatorObj.toString());
            String title = "业务数据同步完成";
            String content = String.format("流程[%s]的审批结果已同步至业务系统", processNo);
            notificationService.sendToUser(initiatorId, NotificationMessage.workflow(title, content, null));
        }

        log.info("业务数据同步完成: processNo={}, businessKey={}, approved={}", processNo, businessKey, approved);
    }
}
