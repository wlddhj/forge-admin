package com.forge.modules.workflow.dto.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * AI智能路由配置（存于网关节点 extendConfig.aiRouting，节点 callAi 非空即视为启用）
 *
 * @author forge-admin
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class AiRoutingConfig {

    /**
     * 自定义路由提示词（描述路由规则，追加在候选分支清单之前）
     */
    private String prompt;

    /**
     * 模型名称（可选，留空走系统默认模型）
     */
    private String modelName;

    /**
     * 决策超时时间（秒），超时按降级链回退 SpEL/默认分支
     */
    private Integer timeoutSeconds = 30;

    /**
     * 置信度阈值（0-100），低于阈值视为不可信，回退降级链
     */
    private Integer confidenceThreshold = 80;

    public int getTimeoutSecondsOrDefault() {
        if (timeoutSeconds == null || timeoutSeconds < 5) {
            return 30;
        }
        return Math.min(timeoutSeconds, 60);
    }

    public int getConfidenceThresholdOrDefault() {
        if (confidenceThreshold == null || confidenceThreshold < 0) {
            return 80;
        }
        return Math.min(confidenceThreshold, 100);
    }
}
