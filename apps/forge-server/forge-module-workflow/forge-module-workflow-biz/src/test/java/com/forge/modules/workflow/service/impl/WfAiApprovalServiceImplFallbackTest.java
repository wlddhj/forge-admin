package com.forge.modules.workflow.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.forge.modules.ai.client.LlmClient;
import com.forge.modules.ai.service.AiModelResolver;
import com.forge.modules.workflow.dto.ai.AiApprovalConfig;
import com.forge.modules.workflow.dto.ai.AiApprovalResult;
import com.forge.modules.workflow.dto.ai.AiApprovalResult.AiStatus;
import com.forge.modules.workflow.dto.ai.AiApprovalResult.Decision;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * buildFallbackResult 降级策略单元测试
 */
class WfAiApprovalServiceImplFallbackTest {

    private final WfAiApprovalServiceImpl service =
            new WfAiApprovalServiceImpl(mock(LlmClient.class), mock(AiModelResolver.class), new ObjectMapper());

    private AiApprovalConfig config(String fallbackStrategy) {
        AiApprovalConfig config = new AiApprovalConfig();
        config.setEnabled(true);
        config.setFallbackStrategy(fallbackStrategy);
        return config;
    }

    @Test
    @DisplayName("DEFAULT_PASS 降级为自动通过")
    void buildFallbackResult_shouldPassByDefault() {
        AiApprovalResult result = service.buildFallbackResult(config("DEFAULT_PASS"), AiStatus.TIMEOUT, "调用超时");

        assertEquals(Decision.APPROVE, result.getDecision());
        assertEquals(AiStatus.TIMEOUT, result.getStatus());
        assertTrue(result.getReasoning().contains("默认通过"));
    }

    @Test
    @DisplayName("DEFAULT_REJECT 降级为自动驳回")
    void buildFallbackResult_shouldRejectByDefault() {
        AiApprovalResult result = service.buildFallbackResult(config("DEFAULT_REJECT"), AiStatus.ERROR, "调用异常");

        assertEquals(Decision.REJECT, result.getDecision());
        assertTrue(result.getReasoning().contains("默认驳回"));
    }

    @Test
    @DisplayName("MANUAL（含未知策略）降级为人工处理")
    void buildFallbackResult_shouldFallbackToManual() {
        AiApprovalResult result = service.buildFallbackResult(config("MANUAL"), AiStatus.TIMEOUT, "调用超时");
        assertEquals(Decision.MANUAL, result.getDecision());
        assertTrue(result.needManualReview());

        AiApprovalResult unknown = service.buildFallbackResult(config("WHATEVER"), AiStatus.TIMEOUT, "调用超时");
        assertEquals(Decision.MANUAL, unknown.getDecision());
    }
}
