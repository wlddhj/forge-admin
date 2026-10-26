package com.forge.modules.workflow.framework.ai;

import com.aizuda.bpm.engine.FlowLongEngine;
import com.aizuda.bpm.engine.core.Execution;
import com.aizuda.bpm.engine.core.FlowCreator;
import com.aizuda.bpm.engine.core.FlowLongContext;
import com.aizuda.bpm.engine.entity.FlwInstance;
import com.aizuda.bpm.engine.model.AiResponse;
import com.aizuda.bpm.engine.model.ConditionNode;
import com.aizuda.bpm.engine.model.NodeModel;
import com.aizuda.bpm.engine.model.ProcessModel;
import com.forge.modules.workflow.service.WfAiRoutingService;
import com.forge.modules.workflow.service.WfAiRoutingService.RoutingDecision;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * FlowAiHandler 适配层单元测试：候选分支解析、降级返回、审批路径 no-op
 */
class ForgeFlowAiHandlerTest {

    private WfAiRoutingService routingService;
    private ForgeFlowAiHandler handler;

    @BeforeEach
    void setUp() {
        routingService = mock(WfAiRoutingService.class);
        handler = new ForgeFlowAiHandler(routingService);
    }

    private ConditionNode branch(String key) {
        ConditionNode branch = new ConditionNode();
        branch.setNodeKey(key);
        branch.setNodeName(key + "名");
        return branch;
    }

    private Execution executionWithInstance(long instanceId) {
        FlwInstance instance = new FlwInstance();
        instance.setId(instanceId);
        return new Execution(mock(FlowLongEngine.class), new ProcessModel(),
                FlowCreator.of("1", "发起人"), instance, null);
    }

    private RoutingDecision decision(String status, List<String> keys) {
        RoutingDecision decision = new RoutingDecision();
        decision.setStatus(status);
        decision.setMatchedNodeKeys(keys);
        return decision;
    }

    @Test
    @DisplayName("条件网关：SUCCESS 取第一个 nodeKey；候选来自 conditionNodes")
    void decideRoute_conditionGateway() {
        NodeModel node = new NodeModel();
        node.setType(4);
        node.setNodeKey("gw1");
        node.setConditionNodes(List.of(branch("k1"), branch("k2")));
        when(routingService.decide(eq(node), anyList(), eq(false), eq(100L), anyMap()))
                .thenReturn(decision("SUCCESS", List.of("k1")));

        String result = handler.decideRoute(mock(FlowLongContext.class), executionWithInstance(100L), node, Map.of());

        assertEquals("k1", result);
        ArgumentCaptor<List<ConditionNode>> captor = ArgumentCaptor.captor();
        verify(routingService).decide(eq(node), captor.capture(), eq(false), eq(100L), anyMap());
        assertEquals(2, captor.getValue().size());
    }

    @Test
    @DisplayName("路由网关(type=23)：候选来自 routeNodes")
    void decideRoute_routeGatewayUsesRouteNodes() {
        NodeModel node = new NodeModel();
        node.setType(23);
        node.setNodeKey("gw2");
        node.setRouteNodes(List.of(branch("r1")));
        node.setConditionNodes(List.of(branch("c1"), branch("c2")));
        when(routingService.decide(eq(node), anyList(), eq(false), isNull(), anyMap()))
                .thenReturn(decision("SUCCESS", List.of("r1")));

        String result = handler.decideRoute(mock(FlowLongContext.class), new Execution(mock(FlowCreator.class), Map.of()), node, Map.of());

        assertEquals("r1", result);
        ArgumentCaptor<List<ConditionNode>> captor = ArgumentCaptor.captor();
        verify(routingService).decide(eq(node), captor.capture(), eq(false), isNull(), anyMap());
        assertEquals(List.of("r1"), captor.getValue().stream().map(ConditionNode::getNodeKey).toList());
    }

    @Test
    @DisplayName("包容网关：SUCCESS 返回全部命中；非 SUCCESS 返回 null")
    void decideInclusiveRoutes_variants() {
        NodeModel node = new NodeModel();
        node.setType(9);
        node.setNodeKey("gw3");
        node.setInclusiveNodes(List.of(branch("i1"), branch("i2")));
        when(routingService.decide(eq(node), anyList(), eq(true), isNull(), anyMap()))
                .thenReturn(decision("SUCCESS", List.of("i1", "i2")))
                .thenReturn(decision("LOW_CONFIDENCE", List.of()));

        Execution execution = new Execution(mock(FlowCreator.class), Map.of());
        assertEquals(List.of("i1", "i2"), handler.decideInclusiveRoutes(mock(FlowLongContext.class), execution, node, Map.of()));
        assertNull(handler.decideInclusiveRoutes(mock(FlowLongContext.class), execution, node, Map.of()));
    }

    @Test
    @DisplayName("决策异常：吞异常返回 null 走引擎降级")
    void decide_exceptionReturnsNull() {
        NodeModel node = new NodeModel();
        node.setType(4);
        node.setNodeKey("gw4");
        node.setConditionNodes(List.of(branch("k1")));
        when(routingService.decide(any(), anyList(), anyBoolean(), any(), anyMap()))
                .thenThrow(new RuntimeException("llm boom"));

        assertNull(handler.decideRoute(mock(FlowLongContext.class), new Execution(mock(FlowCreator.class), Map.of()), node, Map.of()));
    }

    @Test
    @DisplayName("无候选分支：不调服务直接降级")
    void decide_emptyCandidates() {
        NodeModel node = new NodeModel();
        node.setType(4);
        node.setNodeKey("gw5");
        node.setConditionNodes(List.of());

        assertNull(handler.decideRoute(mock(FlowLongContext.class), new Execution(mock(FlowCreator.class), Map.of()), node, Map.of()));
        verifyNoInteractions(routingService);
    }

    @Test
    @DisplayName("引擎 callAi 审批路径：execute 告警 no-op，processAiResponse 放行")
    void approvalPath_noOp() {
        AiResponse response = handler.execute(mock(FlowLongContext.class), new Execution(mock(FlowCreator.class), Map.of()), new NodeModel());
        assertNotNull(response);
        assertTrue(handler.processAiResponse(mock(FlowLongContext.class),
                new Execution(mock(FlowCreator.class), Map.of()), new NodeModel(), response));
    }
}
