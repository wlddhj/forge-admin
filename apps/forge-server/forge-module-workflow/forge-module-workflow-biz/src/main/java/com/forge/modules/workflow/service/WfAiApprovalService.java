package com.forge.modules.workflow.service;

import com.forge.modules.workflow.dto.ai.AiApprovalConfig;
import com.forge.modules.workflow.dto.ai.AiApprovalResult;

import java.util.Map;

/**
 * AI审批服务接口
 *
 * @author forge-admin
 */
public interface WfAiApprovalService {

    /**
     * 执行AI审批决策
     *
     * @param config AI审批配置
     * @param processInstanceId 流程实例ID
     * @param taskId 任务ID
     * @param taskName 任务名称
     * @param variables 流程变量
     * @return AI审批结果
     */
    AiApprovalResult executeAiApproval(AiApprovalConfig config,
                                        String processInstanceId,
                                        String taskId,
                                        String taskName,
                                        Map<String, Object> variables);

    /**
     * 构建AI审批提示词
     *
     * @param config AI审批配置
     * @param taskName 任务名称
     * @param variables 流程变量
     * @return 提示词
     */
    String buildApprovalPrompt(AiApprovalConfig config, String taskName, Map<String, Object> variables);

    /**
     * 从节点扩展配置解析AI审批配置
     *
     * @param extendConfig 扩展配置Map
     * @return AI审批配置，如不存在返回null
     */
    AiApprovalConfig parseAiConfig(Map<String, Object> extendConfig);
}