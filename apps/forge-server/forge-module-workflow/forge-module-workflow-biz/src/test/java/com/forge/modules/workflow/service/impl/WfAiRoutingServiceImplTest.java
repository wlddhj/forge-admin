package com.forge.modules.workflow.service.impl;

import com.aizuda.bpm.engine.model.ConditionNode;
import com.aizuda.bpm.engine.model.NodeModel;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.forge.modules.ai.client.LlmClient;
import com.forge.modules.ai.dto.request.ChatRequest;
import com.forge.modules.ai.dto.response.ChatResponse;
import com.forge.modules.ai.entity.AiModelConfig;
import com.forge.modules.ai.service.AiModelResolver;
import com.forge.modules.workflow.dto.ai.AiRoutingConfig;
import com.forge.modules.workflow.entity.WfAiRouteRecord;
import com.forge.modules.workflow.mapper.WfAiRouteRecordMapper;
import com.forge.modules.workflow.service.WfAiRoutingService.RoutingDecision;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * AI智能路由服务单元测试：配置解析、响应匹配、置信度、降级与审计
 */
class WfAiRoutingServiceImplTest {

    private LlmClient llmClient;
    private AiModelResolver modelResolver;
    private WfAiRouteRecordMapper routeRecordMapper;
    private WfAiRoutingServiceImpl service;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        llmClient = mock(LlmClient.class);
        modelResolver = mock(AiModelResolver.class);
        routeRecordMapper = mock(WfAiRouteRecordMapper.class);
        service = new WfAiRoutingServiceImpl(llmClient, modelResolver, objectMapper, routeRecordMapper);

        AiModelConfig modelConfig = new AiModelConfig();
        modelConfig.setProvider("deepseek");
        when(modelResolver.resolve(any())).thenReturn(modelConfig);
    }

    private NodeModel gatewayNode(Object routingConfig) {
        NodeModel node = new NodeModel();
        node.setNodeKey("gw1");
        node.setNodeName("金额判断");
        node.setType(4);
        Map<String, Object> extendConfig = new HashMap<>();
        extendConfig.put("aiRouting", routingConfig);
        node.setExtendConfig(extendConfig);
        return node;
    }

    private List<ConditionNode> candidates() {
        ConditionNode branchA = new ConditionNode();
        branchA.setNodeKey("flk_branch_a");
        branchA.setNodeName("大额审批");
        ConditionNode branchDefault = new ConditionNode();
        branchDefault.setNodeKey("flk_branch_default");
        branchDefault.setNodeName("默认分支");
        return List.of(branchA, branchDefault);
    }

    private void stubLlm(String content) {
        ChatResponse response = new ChatResponse();
        response.setSuccess(true);
        response.setContent(content);
        response.setModelName("test-model");
        when(llmClient.chat(any(ChatRequest.class), any(AiModelConfig.class))).thenReturn(response);
    }

    @Test
    @DisplayName("配置解析：extendConfig.aiRouting Map/String 均可，缺失返回 null")
    void parseRoutingConfig_variants() {
        Map<String, Object> cfg = Map.of("prompt", "金额大于1万走大额", "confidenceThreshold", 70);
        assertNotNull(service.parseRoutingConfig(gatewayNode(cfg)));
        AiRoutingConfig parsed = service.parseRoutingConfig(gatewayNode(cfg));
        assertEquals("金额大于1万走大额", parsed.getPrompt());
        assertEquals(70, parsed.getConfidenceThresholdOrDefault());

        NodeModel strNode = gatewayNode("{\"timeoutSeconds\": 20, \"unknownField\": 1}");
        AiRoutingConfig strParsed = service.parseRoutingConfig(strNode);
        assertNotNull(strParsed);
        assertEquals(20, strParsed.getTimeoutSecondsOrDefault());

        // 空对象 → 默认值配置（默认值生效，非 null）
        assertEquals(15, service.parseRoutingConfig(gatewayNode(new HashMap<>())).getTimeoutSecondsOrDefault());

        // extendConfig 无 aiRouting 键 → null（未配置）
        NodeModel noKey = new NodeModel();
        noKey.setNodeKey("gw3");
        noKey.setExtendConfig(new HashMap<>());
        assertNull(service.parseRoutingConfig(noKey));
        assertNull(service.parseRoutingConfig(null));

        assertNull(service.parseRoutingConfig(gatewayNode("not-json")));
    }

    @Test
    @DisplayName("配置钳制：超时 5-60s，阈值 0-100")
    void configClamp() {
        AiRoutingConfig config = new AiRoutingConfig();
        assertEquals(15, config.getTimeoutSecondsOrDefault());
        config.setTimeoutSeconds(3);
        assertEquals(15, config.getTimeoutSecondsOrDefault());
        config.setTimeoutSeconds(90);
        assertEquals(60, config.getTimeoutSecondsOrDefault());
        config.setTimeoutSeconds(30);
        assertEquals(30, config.getTimeoutSecondsOrDefault());
        config.setConfidenceThreshold(120);
        assertEquals(100, config.getConfidenceThresholdOrDefault());
    }

    @Test
    @DisplayName("决策成功：nodeKey 精确匹配且置信度达标 → SUCCESS 并落审计")
    void decide_successByNodeKey() {
        stubLlm("{\"nodeKeys\": [\"flk_branch_a\"], \"confidence\": 92, \"reasoning\": \"金额5万\"}");

        RoutingDecision decision = service.decide(gatewayNode(new HashMap<>()), candidates(), false, 100L, Map.of("amount", 50000));

        assertEquals("SUCCESS", decision.getStatus());
        assertEquals(List.of("flk_branch_a"), decision.getMatchedNodeKeys());
        assertEquals(92, decision.getConfidence());

        ArgumentCaptor<WfAiRouteRecord> captor = ArgumentCaptor.forClass(WfAiRouteRecord.class);
        verify(routeRecordMapper, times(1)).insert(captor.capture());
        WfAiRouteRecord record = captor.getValue();
        assertEquals("SUCCESS", record.getStatus());
        assertEquals("flk_branch_a", record.getSelectedBranches());
        assertEquals(100L, record.getProcessInstanceId());
        assertEquals("gw1", record.getGatewayNodeKey());
        assertEquals("deepseek", record.getProvider());
        assertTrue(record.getCandidateBranches().contains("flk_branch_default"));
    }

    @Test
    @DisplayName("分支名兜底匹配：模型返回分支名也能命中")
    void decide_successByBranchName() {
        stubLlm("前置说明文字 {\"nodeKeys\": [\"大额审批\"], \"confidence\": 88, \"reasoning\": \"大额\"} 后置");

        RoutingDecision decision = service.decide(gatewayNode(new HashMap<>()), candidates(), false, 100L, Map.of());

        assertEquals("SUCCESS", decision.getStatus());
        assertEquals(List.of("flk_branch_a"), decision.getMatchedNodeKeys());
    }

    @Test
    @DisplayName("低置信度：低于阈值 → LOW_CONFIDENCE 且不采用")
    void decide_lowConfidenceFallsBack() {
        stubLlm("{\"nodeKeys\": [\"flk_branch_a\"], \"confidence\": 50, \"reasoning\": \"不太确定\"}");

        RoutingDecision decision = service.decide(gatewayNode(new HashMap<>()), candidates(), false, 100L, Map.of());

        assertEquals("LOW_CONFIDENCE", decision.getStatus());
        assertTrue(decision.getMatchedNodeKeys().isEmpty());
        assertEquals(50, decision.getConfidence());
    }

    @Test
    @DisplayName("未知分支：返回不在候选内的 nodeKey → FALLBACK")
    void decide_unknownNodeKeyFallsBack() {
        stubLlm("{\"nodeKeys\": [\"flk_not_exist\"], \"confidence\": 95, \"reasoning\": \"幻觉\"}");

        RoutingDecision decision = service.decide(gatewayNode(new HashMap<>()), candidates(), false, 100L, Map.of());

        assertEquals("FALLBACK", decision.getStatus());
        assertTrue(decision.getMatchedNodeKeys().isEmpty());
        assertEquals(List.of("flk_not_exist"), decision.getRawSelections());
    }

    @Test
    @DisplayName("单选网关：模型返回多个 nodeKey 只取第一个匹配")
    void decide_exclusiveTakesFirstOnly() {
        stubLlm("{\"nodeKeys\": [\"flk_branch_a\", \"flk_branch_default\"], \"confidence\": 90, \"reasoning\": \"多选\"}");

        RoutingDecision decision = service.decide(gatewayNode(new HashMap<>()), candidates(), false, 100L, Map.of());

        assertEquals("SUCCESS", decision.getStatus());
        assertEquals(List.of("flk_branch_a"), decision.getMatchedNodeKeys());
    }

    @Test
    @DisplayName("包容网关：多 nodeKey 全部命中")
    void decide_inclusiveMatchesAll() {
        stubLlm("{\"nodeKeys\": [\"flk_branch_default\", \"flk_branch_a\"], \"confidence\": 90, \"reasoning\": \"并行\"}");

        RoutingDecision decision = service.decide(gatewayNode(new HashMap<>()), candidates(), true, 100L, Map.of());

        assertEquals("SUCCESS", decision.getStatus());
        assertEquals(List.of("flk_branch_default", "flk_branch_a"), decision.getMatchedNodeKeys());
    }

    @Test
    @DisplayName("LLM 调用失败：success=false → ERROR 降级不抛异常")
    void decide_llmFailureFallsBack() {
        ChatResponse response = new ChatResponse();
        response.setSuccess(false);
        response.setErrorMessage("上游不可用");
        when(llmClient.chat(any(ChatRequest.class), any(AiModelConfig.class))).thenReturn(response);

        RoutingDecision decision = assertDoesNotThrow(() ->
                service.decide(gatewayNode(new HashMap<>()), candidates(), false, 100L, Map.of()));

        assertEquals("ERROR", decision.getStatus());
        assertTrue(decision.getMatchedNodeKeys().isEmpty());
        assertTrue(decision.getReasoning().contains("上游不可用"));
    }

    @Test
    @DisplayName("响应不可解析：纯文本 → FALLBACK 降级")
    void decide_unparseableFallsBack() {
        stubLlm("我觉得应该走大额分支");

        RoutingDecision decision = service.decide(gatewayNode(new HashMap<>()), candidates(), false, 100L, Map.of());

        assertEquals("FALLBACK", decision.getStatus());
        assertTrue(decision.getMatchedNodeKeys().isEmpty());
    }

    @Test
    @DisplayName("callAi 已标记但配置缺失：FALLBACK 并落审计")
    void decide_missingConfigFallsBack() {
        NodeModel node = new NodeModel();
        node.setNodeKey("gw2");
        node.setNodeName("无配置网关");

        RoutingDecision decision = service.decide(node, candidates(), false, 100L, Map.of());

        assertEquals("FALLBACK", decision.getStatus());
        verify(routeRecordMapper, times(1)).insert(any(WfAiRouteRecord.class));
        verifyNoInteractions(llmClient);
    }

    @Test
    @DisplayName("无可用模型配置：ERROR 降级且不调 LLM")
    void decide_noModelConfigFallsBack() {
        when(modelResolver.resolve(any())).thenReturn(null);

        RoutingDecision decision = service.decide(gatewayNode(new HashMap<>()), candidates(), false, 100L, Map.of());

        assertEquals("ERROR", decision.getStatus());
        assertTrue(decision.getMatchedNodeKeys().isEmpty());
        assertTrue(decision.getReasoning().contains("未找到可用的AI模型配置"));
        verifyNoInteractions(llmClient);
    }

    @Test
    @DisplayName("审计落库失败不影响路由决策")
    void decide_auditFailureDoesNotBreakRouting() {
        stubLlm("{\"nodeKeys\": [\"flk_branch_a\"], \"confidence\": 92, \"reasoning\": \"ok\"}");
        when(routeRecordMapper.insert(any(WfAiRouteRecord.class))).thenThrow(new RuntimeException("db down"));

        RoutingDecision decision = assertDoesNotThrow(() ->
                service.decide(gatewayNode(new HashMap<>()), candidates(), false, 100L, Map.of()));

        assertEquals("SUCCESS", decision.getStatus());
        assertEquals(List.of("flk_branch_a"), decision.getMatchedNodeKeys());
    }

    @Test
    @DisplayName("提示词构建：自定义规则+候选清单+变量脱敏")
    void buildChatRequest_content() {
        Map<String, Object> cfg = Map.of("prompt", "金额大于1万走大额审批");
        stubLlm("{}");

        service.decide(gatewayNode(cfg), candidates(), false, 100L,
                Map.of("amount", 50000, "password", "should-not-appear"));

        ArgumentCaptor<ChatRequest> captor = ArgumentCaptor.forClass(ChatRequest.class);
        verify(llmClient).chat(captor.capture(), any());
        ChatRequest request = captor.getValue();
        assertTrue(request.getContent().contains("金额大于1万走大额审批"));
        assertTrue(request.getContent().contains("flk_branch_a"));
        assertTrue(request.getContent().contains("大额审批"));
        assertTrue(request.getContent().contains("amount: 50000"));
        assertFalse(request.getContent().contains("should-not-appear"));
        assertEquals(1, request.getMessages().size());
        assertTrue(request.getMessages().get(0).getContent().contains("JSON"));
    }
}
