package com.forge.modules.workflow.dto.ai;

import lombok.Data;

/**
 * AI审批配置
 *
 * @author forge-admin
 */
@Data
public class AiApprovalConfig {

    /**
     * 是否启用AI审批
     */
    private Boolean enabled = false;

    /**
     * AI模型提供商（deepseek/qwen/glm/ernie）
     */
    private String provider;

    /**
     * 模型名称
     */
    private String modelName;

    /**
     * 审批决策阈值（0-100，置信度超过此值自动执行）
     */
    private Integer confidenceThreshold = 80;

    /**
     * 失败时的回退策略（DEFAULT_PASS/DEFAULT_REJECT/MANUAL）
     */
    private String fallbackStrategy = "MANUAL";

    /**
     * 自定义审批提示词
     */
    private String customPrompt;

    /**
     * 审批超时时间（秒）
     */
    private Integer timeoutSeconds = 30;
}