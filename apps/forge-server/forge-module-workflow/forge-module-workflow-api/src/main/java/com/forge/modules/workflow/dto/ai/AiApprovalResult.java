package com.forge.modules.workflow.dto.ai;

import lombok.Data;

/**
 * AI审批结果
 *
 * @author forge-admin
 */
@Data
public class AiApprovalResult {

    /**
     * 审批决策
     */
    private Decision decision;

    /**
     * 置信度（0-100）
     */
    private Integer confidence;

    /**
     * AI分析说明
     */
    private String reasoning;

    /**
     * 原始响应内容
     */
    private String rawResponse;

    /**
     * 执行状态
     */
    private AiStatus status;

    /**
     * 审批决策枚举
     */
    public enum Decision {
        APPROVE,    // 通过
        REJECT,     // 驳回
        MANUAL      // 人工处理
    }

    /**
     * AI执行状态枚举
     */
    public enum AiStatus {
        SUCCESS,            // 成功
        FAILURE,            // 失败
        LOW_CONFIDENCE,     // 置信度不足
        TIMEOUT,            // 超时
        ERROR               // 异常
    }

    /**
     * 判断是否需要人工处理
     */
    public boolean needManualReview() {
        return decision == Decision.MANUAL || status != AiStatus.SUCCESS;
    }
}