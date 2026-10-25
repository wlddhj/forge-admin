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

    /**
     * 构造非成功状态的审批结果并按配置降级
     * 供异步执行链路在调用超时/中断时生成统一的降级决策
     *
     * @param config AI审批配置
     * @param status 执行状态（TIMEOUT/ERROR 等）
     * @param reason 失败原因说明
     * @return 已应用回退策略的审批结果
     */
    AiApprovalResult buildFallbackResult(AiApprovalConfig config,
                                         AiApprovalResult.AiStatus status,
                                         String reason);
}