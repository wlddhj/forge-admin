package com.forge.modules.workflow.framework.ai;

import cn.hutool.core.util.StrUtil;
import com.aizuda.bpm.engine.FlowLongEngine;
import com.aizuda.bpm.engine.core.FlowCreator;
import com.aizuda.bpm.engine.entity.FlwInstance;
import com.aizuda.bpm.engine.entity.FlwTask;
import com.aizuda.bpm.engine.model.NodeModel;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.forge.modules.workflow.dto.ai.AiApprovalConfig;
import com.forge.modules.workflow.dto.ai.AiApprovalResult;
import com.forge.modules.workflow.dto.ai.AiApprovalResult.Decision;
import com.forge.modules.workflow.entity.WfApprovalComment;
import com.forge.modules.workflow.entity.WfAiApprovalRecord;
import com.forge.modules.workflow.framework.ApprovalActionTypeEnum;
import com.forge.modules.workflow.mapper.WfAiApprovalRecordMapper;
import com.forge.modules.workflow.mapper.WfApprovalCommentMapper;
import com.forge.modules.workflow.service.WfAiApprovalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * AI审批执行器
 * 在任务创建时检查节点是否配置了AI审批，并自动执行
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
     * 检查并执行AI审批
     *
     * @param task 当前任务
     * @param nodeModel 节点模型
     * @param flowCreator 流程创建者
     * @return 是否成功执行了自动审批
     */
    public boolean checkAndExecuteAiApproval(FlwTask task, NodeModel nodeModel, FlowCreator flowCreator) {
        // 1. 解析AI审批配置
        Map<String, Object> extendConfig = nodeModel.getExtendConfig();
        AiApprovalConfig config = aiApprovalService.parseAiConfig(extendConfig);

        if (config == null || !Boolean.TRUE.equals(config.getEnabled())) {
            log.debug("节点未配置AI审批或未启用: taskKey={}", nodeModel.getNodeKey());
            return false;
        }

        log.info("开始执行AI审批: taskId={}, taskName={}, provider={}",
                task.getId(), task.getTaskName(), config.getProvider());

        // 2. 获取流程变量
        Map<String, Object> variables = getProcessVariables(task.getInstanceId());

        // 3. 执行AI审批决策
        AiApprovalResult result = aiApprovalService.executeAiApproval(
                config,
                String.valueOf(task.getInstanceId()),
                String.valueOf(task.getId()),
                task.getTaskName(),
                variables
        );

        // 4. 保存AI审批记录
        saveAiApprovalRecord(task, config, result);

        // 5. 根据决策结果执行审批动作
        if (result.getDecision() == Decision.APPROVE) {
            // 自动通过
            executeApprove(task, flowCreator, result);
            return true;
        } else if (result.getDecision() == Decision.REJECT) {
            // 自动驳回
            executeReject(task, flowCreator, result);
            return true;
        } else {
            // 人工处理，不执行自动审批
            log.info("AI审批结果为人工处理: taskId={}, reasoning={}",
                    task.getId(), result.getReasoning());
            return false;
        }
    }

    /**
     * 检查节点是否配置了AI审批
     */
    public boolean hasAiApprovalConfig(NodeModel nodeModel) {
        if (nodeModel == null || nodeModel.getExtendConfig() == null) {
            return false;
        }
        AiApprovalConfig config = aiApprovalService.parseAiConfig(nodeModel.getExtendConfig());
        return config != null && Boolean.TRUE.equals(config.getEnabled());
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