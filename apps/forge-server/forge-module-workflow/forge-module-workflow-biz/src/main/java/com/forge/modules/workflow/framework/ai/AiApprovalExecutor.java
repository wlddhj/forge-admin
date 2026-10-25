package com.forge.modules.workflow.framework.ai;

import cn.hutool.core.util.StrUtil;
import com.aizuda.bpm.engine.FlowLongEngine;
import com.aizuda.bpm.engine.core.FlowCreator;
import com.aizuda.bpm.engine.entity.FlwInstance;
import com.aizuda.bpm.engine.entity.FlwTask;
import com.aizuda.bpm.engine.model.NodeModel;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.forge.framework.tenant.core.context.TenantContextHolder;
import com.forge.modules.workflow.dto.ai.AiApprovalConfig;
import com.forge.modules.workflow.dto.ai.AiApprovalResult;
import com.forge.modules.workflow.dto.ai.AiApprovalResult.AiStatus;
import com.forge.modules.workflow.dto.ai.AiApprovalResult.Decision;
import com.forge.modules.workflow.entity.WfApprovalComment;
import com.forge.modules.workflow.entity.WfAiApprovalRecord;
import com.forge.modules.workflow.framework.ApprovalActionTypeEnum;
import com.forge.modules.workflow.framework.FlowCreatorFactory;
import com.forge.modules.workflow.listener.event.WorkflowAiApprovalEvent;
import com.forge.modules.workflow.mapper.WfAiApprovalRecordMapper;
import com.forge.modules.workflow.mapper.WfApprovalCommentMapper;
import com.forge.modules.workflow.service.WfAiApprovalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * AI审批执行器
 *
 * 分两段执行：
 * 1. 同步段 {@link #buildAiApprovalEvent}：任务创建事件内仅解析节点配置，判定需要 AI 审批时发布事件，
 *    不做任何 LLM 调用，不阻塞引擎流转事务；
 * 2. 异步段 {@link #executeForEvent}：事务提交后由 AiApprovalEventConsumer 在 workflowExecutor 线程执行，
 *    按节点 timeoutSeconds 控制调用时长，超时/异常按 fallbackStrategy 降级。
 *
 * @author forge-admin
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiApprovalExecutor {

    private final WfAiApprovalService aiApprovalService;
    private final FlowLongEngine flowLongEngine;
    private final WfAiApprovalRecordMapper aiApprovalRecordMapper;
    private final WfApprovalCommentMapper approvalCommentMapper;
    private final ObjectMapper objectMapper;

    /**
     * 同步段：解析节点配置并构建AI审批事件
     *
     * @return 无需AI审批（未配置/未启用）时返回 null
     */
    public WorkflowAiApprovalEvent buildAiApprovalEvent(FlwTask task, NodeModel nodeModel, FlowCreator flowCreator) {
        AiApprovalConfig config = aiApprovalService.parseAiConfig(nodeModel.getExtendConfig());
        if (config == null || !Boolean.TRUE.equals(config.getEnabled())) {
            log.debug("节点未配置AI审批或未启用: taskKey={}", nodeModel.getNodeKey());
            return null;
        }
        return new WorkflowAiApprovalEvent(this, task.getId(), config,
                flowCreator != null ? flowCreator.getCreateId() : null,
                flowCreator != null ? flowCreator.getCreateBy() : null,
                TenantContextHolder.getTenantId());
    }

    /**
     * 异步段：执行AI审批决策并按结果自动处理任务
     */
    public void executeForEvent(WorkflowAiApprovalEvent event) {
        AiApprovalConfig config = event.getAiConfig();

        // 幂等守卫：任务已被人工办理或归档则放弃执行
        FlwTask task = flowLongEngine.queryService().getTask(event.getTaskId());
        if (task == null) {
            log.info("AI审批放弃执行：任务已不在活动表（可能已被办理）: taskId={}", event.getTaskId());
            return;
        }

        log.info("开始执行AI审批: taskId={}, taskName={}, provider={}",
                task.getId(), task.getTaskName(), config.getProvider());

        Map<String, Object> variables = getProcessVariables(task.getInstanceId());
        AiApprovalResult result = executeWithTimeout(config, task, variables);

        saveAiApprovalRecord(task, config, result);

        FlowCreator operator = FlowCreatorFactory.restore(event.getOperatorId(), event.getOperatorName());
        if (result.getDecision() == Decision.APPROVE) {
            executeApprove(task, operator, result);
        } else if (result.getDecision() == Decision.REJECT) {
            executeReject(task, operator, result);
        } else {
            log.info("AI审批结果为人工处理: taskId={}, reasoning={}", task.getId(), result.getReasoning());
        }
    }

    /**
     * 按节点 timeoutSeconds 限时执行 LLM 决策，超时按 fallbackStrategy 降级
     */
    private AiApprovalResult executeWithTimeout(AiApprovalConfig config, FlwTask task, Map<String, Object> variables) {
        int timeoutSeconds = config.getTimeoutSeconds() != null && config.getTimeoutSeconds() > 0
                ? config.getTimeoutSeconds() : 30;

        CompletableFuture<AiApprovalResult> future = CompletableFuture.supplyAsync(() ->
                aiApprovalService.executeAiApproval(
                        config,
                        String.valueOf(task.getInstanceId()),
                        String.valueOf(task.getId()),
                        task.getTaskName(),
                        variables));
        try {
            return future.get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            log.warn("AI审批调用超时({}s): taskId={}", timeoutSeconds, task.getId());
            return aiApprovalService.buildFallbackResult(config, AiStatus.TIMEOUT,
                    "AI审批调用超时（" + timeoutSeconds + "秒）");
        } catch (Exception e) {
            future.cancel(true);
            log.error("AI审批调用中断: taskId={}", task.getId(), e);
            return aiApprovalService.buildFallbackResult(config, AiStatus.ERROR,
                    "AI审批调用异常：" + e.getMessage());
        }
    }

    private Map<String, Object> getProcessVariables(Long instanceId) {
        // 从流程实例获取变量
        try {
            FlwInstance instance = flowLongEngine.queryService().getInstance(instanceId);
            if (instance != null && StrUtil.isNotBlank(instance.getVariable())) {
                return objectMapper.readValue(instance.getVariable(), Map.class);
            }
        } catch (Exception e) {
            log.warn("获取流程变量失败: instanceId={}", instanceId, e);
        }
        return new HashMap<>();
    }

    private void executeApprove(FlwTask task, FlowCreator flowCreator, AiApprovalResult result) {
        try {
            Map<String, Object> variables = new HashMap<>();
            variables.put("approved", true);
            variables.put("aiApprovalResult", result.getDecision().name());
            variables.put("aiApprovalConfidence", result.getConfidence());
            variables.put("aiApprovalReasoning", result.getReasoning());

            flowLongEngine.executeTask(task.getId(), flowCreator, variables);

            // 保存审批意见记录
            saveApprovalComment(task, "AI系统", ApprovalActionTypeEnum.APPROVE.getCode(),
                    "AI审批自动通过。置信度：" + result.getConfidence() + "。理由：" + result.getReasoning());

            log.info("AI审批自动通过: taskId={}, confidence={}, reasoning={}",
                    task.getId(), result.getConfidence(), result.getReasoning());
        } catch (Exception e) {
            log.error("AI审批自动通过执行失败: taskId={}", task.getId(), e);
        }
    }

    private void executeReject(FlwTask task, FlowCreator flowCreator, AiApprovalResult result) {
        try {
            Map<String, Object> variables = new HashMap<>();
            variables.put("approved", false);
            variables.put("aiApprovalResult", result.getDecision().name());
            variables.put("aiApprovalConfidence", result.getConfidence());
            variables.put("aiApprovalReasoning", result.getReasoning());

            // 使用 TaskService 的 rejectTask 方法驳回任务
            flowLongEngine.taskService().rejectTask(task, flowCreator, variables);

            // 保存审批意见记录
            saveApprovalComment(task, "AI系统", ApprovalActionTypeEnum.REJECT.getCode(),
                    "AI审批自动驳回。置信度：" + result.getConfidence() + "。理由：" + result.getReasoning());

            log.info("AI审批自动驳回: taskId={}, confidence={}, reasoning={}",
                    task.getId(), result.getConfidence(), result.getReasoning());
        } catch (Exception e) {
            log.error("AI审批自动驳回执行失败: taskId={}", task.getId(), e);
        }
    }

    private void saveApprovalComment(FlwTask task, String operatorName, String actionType, String commentText) {
        WfApprovalComment comment = new WfApprovalComment();
        comment.setProcessInstanceId(task.getInstanceId());
        comment.setTaskId(task.getId());
        comment.setTaskDefKey(task.getTaskKey());
        comment.setTaskName(task.getTaskName());
        comment.setUserId(0L); // AI系统用户ID为0
        comment.setUserName(operatorName);
        comment.setActionType(actionType);
        comment.setCommentText(commentText);
        comment.setCreateTime(LocalDateTime.now());
        approvalCommentMapper.insert(comment);
    }

    private void saveAiApprovalRecord(FlwTask task, AiApprovalConfig config, AiApprovalResult result) {
        WfAiApprovalRecord record = new WfAiApprovalRecord();
        record.setProcessInstanceId(task.getInstanceId());
        record.setTaskId(task.getId());
        record.setTaskDefKey(task.getTaskKey());
        record.setTaskName(task.getTaskName());
        record.setDecision(result.getDecision().name());
        record.setConfidence(result.getConfidence());
        record.setReasoning(result.getReasoning());
        record.setRawResponse(result.getRawResponse());
        record.setStatus(result.getStatus().name());
        record.setProvider(config.getProvider());
        record.setModelName(config.getModelName());
        record.setCreateTime(LocalDateTime.now());

        aiApprovalRecordMapper.insert(record);
    }
}
