package com.forge.modules.workflow.framework.diagram;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 流程图生成器测试：网关分支坐标归一化（负坐标裁切修复回归）
 */
class FlowLongDiagramGeneratorTest {

    private FlowLongDiagramGenerator generator;

    @BeforeEach
    void setUp() {
        generator = new FlowLongDiagramGenerator(new ObjectMapper());
    }

    private String generate(String modelJson) {
        InputStream in = generator.generateDiagram(modelJson, Set.of("ap_low"));
        try (Scanner scanner = new Scanner(in, StandardCharsets.UTF_8)) {
            return scanner.useDelimiter("\\A").next();
        }
    }

    /**
     * 发起人 → 条件网关（两分支：大额→总经理审批 / 默认→主管审批），网关为主链第二个节点
     */
    private String gatewayModel() {
        return """
                {"name":"AI智能分发","key":"demo_ai_route",
                 "nodeConfig":{"nodeName":"发起人","nodeKey":"start","type":0,
                   "childNode":{"nodeName":"AI智能分发","nodeKey":"gw_ai","type":4,"callAi":"1",
                     "extendConfig":{"aiRouting":{"prompt":"x","confidenceThreshold":80,"timeoutSeconds":15}},
                     "conditionNodes":[
                       {"nodeName":"大额","nodeKey":"br_high","type":3,"priorityLevel":1,
                        "conditionList":[[{"label":"金额","field":"amount","operator":">","value":"10000"}]],
                        "childNode":{"nodeName":"总经理审批","nodeKey":"ap_gm","type":1,"setType":1,"examineMode":3,
                          "nodeAssigneeList":[{"id":"102","name":"李四"}]}},
                       {"nodeName":"默认","nodeKey":"br_low","type":3,"priorityLevel":2,"conditionList":[],
                        "childNode":{"nodeName":"主管审批","nodeKey":"ap_low","type":1,"setType":1,"examineMode":3,
                          "nodeAssigneeList":[{"id":"101","name":"张三"}]}}
                     ]}}}
                """;
    }

    @Test
    @DisplayName("条件网关两分支：SVG 无负坐标（左侧分支不再被画布裁掉）")
    void gatewayDiagram_noNegativeCoordinates() {
        String svg = generate(gatewayModel());

        // 所有 x="/y="/cx="/transform 平移坐标均不应为负
        assertFalse(svg.matches("(?s).*[\\s\"]x=\"-\\d+.*"), "SVG 存在负 x 坐标，左缘节点会被裁掉:\n" + svg);
        assertFalse(svg.matches("(?s).*[\\s\"]cx=\"-\\d+.*"), "SVG 存在负 cx 坐标");
    }

    @Test
    @DisplayName("条件网关两分支：两个分支审批节点与分支标签均完整绘制")
    void gatewayDiagram_allBranchNodesDrawn() {
        String svg = generate(gatewayModel());

        assertTrue(svg.contains("总经理审批"), "左分支节点缺失");
        assertTrue(svg.contains("主管审批"), "右分支节点缺失");
        assertTrue(svg.contains("大额"), "分支标签缺失");
        assertTrue(svg.contains("默认"), "默认分支标签缺失");
        assertTrue(svg.contains("AI智能分发"), "网关节点缺失");
    }

    @Test
    @DisplayName("合成结束节点：两条分支链尾汇聚到唯一\"结束\"，且不重复")
    void gatewayDiagram_singleSynthesizedEndNode() {
        String svg = generate(gatewayModel());

        int endCount = svg.split(">结束<", -1).length - 1;
        assertEquals(1, endCount, "应恰好合成一个结束节点");
        assertTrue(svg.contains("结束"), "结束节点缺失");
    }

    @Test
    @DisplayName("模型已显式含结束节点：不再合成重复结束")
    void explicitEndNode_notDuplicated() {
        String model = """
                {"name":"显式结束","key":"t_explicit_end",
                 "nodeConfig":{"nodeName":"发起人","nodeKey":"start","type":0,
                   "childNode":{"nodeName":"审批","nodeKey":"ap1","type":1,"setType":1,"examineMode":3,
                     "nodeAssigneeList":[{"id":"101","name":"张三"}],
                     "childNode":{"nodeName":"结束","nodeKey":"end1","type":-1}}}}
                """;
        String svg = generate(model);

        assertEquals(1, svg.split(">结束<", -1).length - 1, "显式结束存在时不应再合成");
    }

    @Test
    @DisplayName("仅发起人节点的空流程：发起人 → 结束")
    void startOnlyFlow_synthesizesEnd() {
        String model = """
                {"name":"空流程","key":"t_start_only",
                 "nodeConfig":{"nodeName":"发起人","nodeKey":"start","type":0}}
                """;
        String svg = generate(model);

        assertTrue(svg.contains("发起人"));
        assertEquals(1, svg.split(">结束<", -1).length - 1, "空流程也应补结束节点");
    }

    @Test
    @DisplayName("多分支包容网关（3 分支）同样无负坐标")
    void inclusiveDiagram_noNegativeCoordinates() {
        String model = """
                {"name":"包容","key":"t_inc",
                 "nodeConfig":{"nodeName":"发起人","nodeKey":"start","type":0,
                   "childNode":{"nodeName":"审查分发","nodeKey":"gw_inc","type":9,
                     "inclusiveNodes":[
                       {"nodeName":"法务","nodeKey":"b1","type":3,"priorityLevel":1,
                        "childNode":{"nodeName":"法务审查","nodeKey":"ap1","type":1,"setType":1,"examineMode":3,
                          "nodeAssigneeList":[{"id":"105","name":"钱七"}]}},
                       {"nodeName":"换汇","nodeKey":"b2","type":3,"priorityLevel":2,
                        "childNode":{"nodeName":"换汇审查","nodeKey":"ap2","type":1,"setType":1,"examineMode":3,
                          "nodeAssigneeList":[{"id":"103","name":"王五"}]}},
                       {"nodeName":"默认","nodeKey":"b3","type":3,"priorityLevel":3,"conditionList":[],
                        "childNode":{"nodeName":"主管确认","nodeKey":"ap3","type":1,"setType":1,"examineMode":3,
                          "nodeAssigneeList":[{"id":"101","name":"张三"}]}}
                     ]}}}
                """;
        String svg = generate(model);

        assertFalse(svg.matches("(?s).*[\\s\"]x=\"-\\d+.*"), "包容网关存在负 x 坐标");
        assertTrue(svg.contains("法务审查"));
        assertTrue(svg.contains("换汇审查"));
        assertTrue(svg.contains("主管确认"));
    }

    @Test
    @DisplayName("活动节点高亮：activeNodes 命中节点带 node-active 类")
    void gatewayDiagram_activeNodeHighlighted() {
        String svg = generate(gatewayModel());
        assertTrue(svg.contains("node-active"), "活动节点应有高亮样式");
    }

    @Test
    @DisplayName("非法模型 JSON：返回兜底空图不抛异常")
    void invalidModel_fallbackDiagram() {
        String svg = generate("not-json{{");
        assertTrue(svg.contains("流程模型加载失败"));
    }
}
