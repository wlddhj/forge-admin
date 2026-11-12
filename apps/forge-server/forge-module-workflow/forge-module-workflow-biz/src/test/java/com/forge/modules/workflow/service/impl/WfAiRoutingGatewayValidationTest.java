package com.forge.modules.workflow.service.impl;

import com.aizuda.bpm.engine.ProcessService;
import com.aizuda.bpm.engine.model.ConditionNode;
import com.aizuda.bpm.engine.model.NodeModel;
import com.forge.common.exception.BusinessException;
import com.forge.modules.workflow.framework.validator.WfProcessModelValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * AI路由网关发起校验单元测试（校验器公共方法直测，原 doCallRealMethod 免依赖写法已随校验器拆分转正）
 */
class WfAiRoutingGatewayValidationTest {

    private WfProcessModelValidator validator;

    @BeforeEach
    void setUp() {
        validator = new WfProcessModelValidator(mock(ProcessService.class));
    }

    private ConditionNode branch(String key, String name, boolean withCondition) {
        ConditionNode branch = new ConditionNode();
        branch.setNodeKey(key);
        branch.setNodeName(name);
        if (withCondition) {
            branch.setConditionList(List.of(List.of(new com.aizuda.bpm.engine.model.NodeExpression())));
        }
        return branch;
    }

    @Test
    @DisplayName("未启用AI路由(callAi=null)：任何节点直接放行")
    void pass_whenCallAiAbsent() {
        assertDoesNotThrow(() -> validator.checkAiRoutingGateway(new NodeModel()));
    }

    @Test
    @DisplayName("条件网关启用AI路由且有默认分支：放行")
    void pass_conditionGatewayWithDefaultBranch() {
        NodeModel node = new NodeModel();
        node.setType(4);
        node.setNodeKey("gw1");
        node.setNodeName("条件网关");
        node.setCallAi("1");
        node.setConditionNodes(List.of(branch("b1", "分支1", true), branch("b2", "默认", false)));
        assertDoesNotThrow(() -> validator.checkAiRoutingGateway(node));
    }

    @Test
    @DisplayName("AI路由网关缺默认分支：报400")
    void reject_whenNoDefaultBranch() {
        NodeModel node = new NodeModel();
        node.setType(4);
        node.setNodeKey("gw2");
        node.setNodeName("金额网关");
        node.setCallAi("1");
        node.setConditionNodes(List.of(branch("b1", "分支1", true), branch("b2", "分支2", true)));

        BusinessException e = assertThrows(BusinessException.class, () -> validator.checkAiRoutingGateway(node));
        assertTrue(e.getMessage().contains("默认分支"));
    }

    @Test
    @DisplayName("包容/路由网关分别校验 inclusiveNodes/routeNodes")
    void reject_inclusiveAndRouteGateways() {
        NodeModel inclusive = new NodeModel();
        inclusive.setType(9);
        inclusive.setNodeKey("gw3");
        inclusive.setNodeName("包容网关");
        inclusive.setCallAi("1");
        inclusive.setInclusiveNodes(List.of(branch("i1", "分支1", true)));
        assertThrows(BusinessException.class, () -> validator.checkAiRoutingGateway(inclusive));

        NodeModel route = new NodeModel();
        route.setType(23);
        route.setNodeKey("gw4");
        route.setNodeName("路由网关");
        route.setCallAi("1");
        route.setRouteNodes(List.of(branch("r1", "路由1", true), branch("r2", "默认", false)));
        assertDoesNotThrow(() -> validator.checkAiRoutingGateway(route));
    }

    @Test
    @DisplayName("非分支节点配置AI路由：报400")
    void reject_nonGatewayNodeWithCallAi() {
        NodeModel node = new NodeModel();
        node.setType(1);
        node.setNodeKey("ap1");
        node.setNodeName("审批节点");
        node.setCallAi("1");

        BusinessException e = assertThrows(BusinessException.class, () -> validator.checkAiRoutingGateway(node));
        assertTrue(e.getMessage().contains("仅分支节点"));
    }
}
