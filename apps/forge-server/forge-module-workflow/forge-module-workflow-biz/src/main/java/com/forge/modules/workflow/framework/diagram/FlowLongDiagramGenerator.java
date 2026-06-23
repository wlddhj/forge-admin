package com.forge.modules.workflow.framework.diagram;

import com.aizuda.bpm.engine.model.NodeModel;
import com.aizuda.bpm.engine.model.ProcessModel;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * FlowLong 流程图生成器
 * 将 FlowLong JSON 格式的流程定义转换为 SVG 流程图
 *
 * @author forge-admin
 */
@Slf4j
@Component
public class FlowLongDiagramGenerator {

    private final ObjectMapper objectMapper;

    // 节点尺寸常量
    private static final int NODE_WIDTH = 180;
    private static final int NODE_HEIGHT = 80;
    private static final int START_END_RADIUS = 30;
    private static final int HORIZONTAL_GAP = 60;
    private static final int VERTICAL_GAP = 40;

    // 颜色常量
    private static final String COLOR_START = "#1890ff";
    private static final String COLOR_APPROVAL = "#fa8c16";
    private static final String COLOR_CC = "#52c41a";
    private static final String COLOR_END = "#f5222d";
    private static final String COLOR_CONDITION = "#722ed1";
    private static final String COLOR_ACTIVE = "#409eff";
    private static final String COLOR_LINE = "#d9d9d9";
    private static final String COLOR_TEXT = "#303133";

    public FlowLongDiagramGenerator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 生成流程图 SVG
     *
     * @param modelJson    FlowLong JSON 格式的流程定义
     * @param activeNodes  当前活动节点 Key 列表（用于高亮）
     * @return SVG InputStream
     */
    public InputStream generateDiagram(String modelJson, Set<String> activeNodes) {
        try {
            ProcessModel processModel = objectMapper.readValue(modelJson, ProcessModel.class);
            String svg = generateSvg(processModel, activeNodes != null ? activeNodes : Collections.emptySet());
            return new ByteArrayInputStream(svg.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.error("解析流程模型失败", e);
            return generateEmptyDiagram();
        }
    }

    /**
     * 生成空流程图（当模型解析失败时）
     */
    private InputStream generateEmptyDiagram() {
        String svg = """
                <svg xmlns="http://www.w3.org/2000/svg" width="400" height="200">
                  <text x="200" y="100" text-anchor="middle" fill="#909399" font-size="14">流程模型加载失败</text>
                </svg>
                """;
        return new ByteArrayInputStream(svg.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 生成 SVG 流程图
     */
    private String generateSvg(ProcessModel processModel, Set<String> activeNodes) {
        // 计算画布尺寸
        CanvasLayout layout = calculateLayout(processModel.getNodeConfig(), 0, 0);

        int canvasWidth = layout.maxX + NODE_WIDTH + 100;
        int canvasHeight = layout.maxY + NODE_HEIGHT + 100;

        StringBuilder svg = new StringBuilder();
        svg.append(String.format("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"%d\" height=\"%d\">\n",
                canvasWidth, canvasHeight));

        // 添加样式定义
        svg.append(generateStyles());

        // 递归绘制节点
        drawNode(svg, processModel.getNodeConfig(), 100, 50, activeNodes, layout.nodePositions);

        svg.append("</svg>");
        return svg.toString();
    }

    /**
     * 生成 SVG 样式
     */
    private String generateStyles() {
        return """
                <defs>
                  <style>
                    .node-box { rx: 8; ry: 8; stroke-width: 2; }
                    .node-active { stroke: #409eff; stroke-width: 3; }
                    .node-text { font-size: 14px; font-weight: 600; fill: #303133; }
                    .node-desc { font-size: 12px; fill: #909399; }
                    .line { stroke: #d9d9d9; stroke-width: 2; fill: none; }
                    .arrow { fill: #d9d9d9; }
                  </style>
                </defs>
                """;
    }

    /**
     * 计算节点布局
     */
    private CanvasLayout calculateLayout(NodeModel node, int x, int y) {
        CanvasLayout layout = new CanvasLayout();
        layout.nodePositions = new HashMap<>();

        calculateNodePosition(node, x, y, layout);
        return layout;
    }

    /**
     * 递归计算节点位置
     */
    private void calculateNodePosition(NodeModel node, int x, int y, CanvasLayout layout) {
        if (node == null) return;

        layout.nodePositions.put(node.getNodeKey(), new NodePosition(x, y));
        layout.maxX = Math.max(layout.maxX, x);
        layout.maxY = Math.max(layout.maxY, y);

        // 计算子节点位置
        if (node.getChildNode() != null) {
            int childY = y + NODE_HEIGHT + VERTICAL_GAP;
            calculateNodePosition(node.getChildNode(), x, childY, layout);
        }

        // 计算条件分支节点位置（横向排列）
        if (node.getConditionNodes() != null && !node.getConditionNodes().isEmpty()) {
            int branchCount = node.getConditionNodes().size();
            int branchStartX = x - (branchCount - 1) * (NODE_WIDTH + HORIZONTAL_GAP) / 2;
            for (int i = 0; i < branchCount; i++) {
                NodeModel conditionChild = node.getConditionNodes().get(i).getChildNode();
                if (conditionChild != null) {
                    int branchX = branchStartX + i * (NODE_WIDTH + HORIZONTAL_GAP);
                    int branchY = y + NODE_HEIGHT + VERTICAL_GAP;
                    calculateNodePosition(conditionChild, branchX, branchY, layout);
                }
            }
        }
    }

    /**
     * 绘制节点
     */
    private void drawNode(StringBuilder svg, NodeModel node, int x, int y,
                          Set<String> activeNodes, Map<String, NodePosition> positions) {
        if (node == null) return;

        String nodeKey = node.getNodeKey();
        boolean isActive = activeNodes.contains(nodeKey);

        // 根据节点类型绘制
        Integer type = node.getType();
        if (type == null) type = 1;

        switch (type) {
            case 1: // 开始节点
                drawStartNode(svg, x, y, node.getNodeName(), isActive);
                break;
            case 4: // 结束节点
                drawEndNode(svg, x, y, node.getNodeName(), isActive);
                break;
            case 2: // 审批节点
                drawApprovalNode(svg, x, y, node.getNodeName(), getCandidateText(node), isActive);
                break;
            case 3: // 抄送节点
                drawCcNode(svg, x, y, node.getNodeName(), getCcText(node), isActive);
                break;
            case 5: // 条件分支
                drawConditionNode(svg, x, y, node.getNodeName(), isActive);
                break;
            default:
                drawGenericNode(svg, x, y, node.getNodeName(), type, isActive);
        }

        // 绘制连线到子节点
        if (node.getChildNode() != null) {
            NodePosition childPos = positions.get(node.getChildNode().getNodeKey());
            if (childPos != null) {
                drawLine(svg, x, y + NODE_HEIGHT / 2, childPos.x, childPos.y - NODE_HEIGHT / 2, isActive);
            }
            drawNode(svg, node.getChildNode(), childPos.x, childPos.y, activeNodes, positions);
        }

        // 绘制条件分支连线
        if (node.getConditionNodes() != null && !node.getConditionNodes().isEmpty()) {
            for (var conditionNode : node.getConditionNodes()) {
                NodeModel conditionChild = conditionNode.getChildNode();
                if (conditionChild != null) {
                    NodePosition childPos = positions.get(conditionChild.getNodeKey());
                    if (childPos != null) {
                        drawBranchLine(svg, x, y + NODE_HEIGHT / 2, childPos.x, childPos.y - NODE_HEIGHT / 2,
                                conditionNode.getNodeName(), isActive);
                    }
                    drawNode(svg, conditionChild, childPos.x, childPos.y, activeNodes, positions);
                }
            }
        }
    }

    /**
     * 绘制开始节点
     */
    private void drawStartNode(StringBuilder svg, int x, int y, String name, boolean isActive) {
        String color = isActive ? COLOR_ACTIVE : COLOR_START;
        svg.append(String.format(
                "<circle cx=\"%d\" cy=\"%d\" r=\"%d\" fill=\"%s\" class=\"node-box%s\"/>\n",
                x + NODE_WIDTH / 2, y + START_END_RADIUS, START_END_RADIUS, color,
                isActive ? " node-active" : ""));
        svg.append(String.format(
                "<text x=\"%d\" y=\"%d\" text-anchor=\"middle\" class=\"node-text\">%s</text>\n",
                x + NODE_WIDTH / 2, y + START_END_RADIUS + 5, escapeText(name)));
    }

    /**
     * 绘制结束节点
     */
    private void drawEndNode(StringBuilder svg, int x, int y, String name, boolean isActive) {
        String color = isActive ? COLOR_ACTIVE : COLOR_END;
        svg.append(String.format(
                "<circle cx=\"%d\" cy=\"%d\" r=\"%d\" fill=\"%s\" stroke=\"%s\" class=\"node-box%s\"/>\n",
                x + NODE_WIDTH / 2, y + START_END_RADIUS, START_END_RADIUS - 5, "#fff", color,
                isActive ? " node-active" : ""));
        svg.append(String.format(
                "<circle cx=\"%d\" cy=\"%d\" r=\"%d\" fill=\"%s\" class=\"node-box\"/>\n",
                x + NODE_WIDTH / 2, y + START_END_RADIUS, START_END_RADIUS - 10, color));
        svg.append(String.format(
                "<text x=\"%d\" y=\"%d\" text-anchor=\"middle\" class=\"node-text\">%s</text>\n",
                x + NODE_WIDTH / 2, y + START_END_RADIUS + 5, escapeText(name)));
    }

    /**
     * 绘制审批节点
     */
    private void drawApprovalNode(StringBuilder svg, int x, int y, String name, String desc, boolean isActive) {
        String color = isActive ? COLOR_ACTIVE : COLOR_APPROVAL;
        svg.append(String.format(
                "<rect x=\"%d\" y=\"%d\" width=\"%d\" height=\"%d\" fill=\"#fff\" stroke=\"%s\" class=\"node-box%s\"/>\n",
                x, y, NODE_WIDTH, NODE_HEIGHT, color, isActive ? " node-active" : ""));
        svg.append(String.format(
                "<text x=\"%d\" y=\"%d\" text-anchor=\"middle\" class=\"node-text\">%s</text>\n",
                x + NODE_WIDTH / 2, y + 25, escapeText(name)));
        if (desc != null && !desc.isEmpty()) {
            svg.append(String.format(
                    "<text x=\"%d\" y=\"%d\" text-anchor=\"middle\" class=\"node-desc\">%s</text>\n",
                    x + NODE_WIDTH / 2, y + 50, escapeText(desc)));
        }
    }

    /**
     * 绘制抄送节点
     */
    private void drawCcNode(StringBuilder svg, int x, int y, String name, String desc, boolean isActive) {
        String color = isActive ? COLOR_ACTIVE : COLOR_CC;
        svg.append(String.format(
                "<rect x=\"%d\" y=\"%d\" width=\"%d\" height=\"%d\" fill=\"#fff\" stroke=\"%s\" class=\"node-box%s\"/>\n",
                x, y, NODE_WIDTH, NODE_HEIGHT, color, isActive ? " node-active" : ""));
        svg.append(String.format(
                "<text x=\"%d\" y=\"%d\" text-anchor=\"middle\" class=\"node-text\">%s</text>\n",
                x + NODE_WIDTH / 2, y + 25, escapeText(name)));
        if (desc != null && !desc.isEmpty()) {
            svg.append(String.format(
                    "<text x=\"%d\" y=\"%d\" text-anchor=\"middle\" class=\"node-desc\">%s</text>\n",
                    x + NODE_WIDTH / 2, y + 50, escapeText(desc)));
        }
    }

    /**
     * 绘制条件分支节点
     */
    private void drawConditionNode(StringBuilder svg, int x, int y, String name, boolean isActive) {
        String color = isActive ? COLOR_ACTIVE : COLOR_CONDITION;
        // 绘制菱形
        int cx = x + NODE_WIDTH / 2;
        int cy = y + NODE_HEIGHT / 2;
        int hw = NODE_WIDTH / 2;
        int hh = NODE_HEIGHT / 2;
        svg.append(String.format(
                "<polygon points=\"%d,%d %d,%d %d,%d %d,%d\" fill=\"#fff\" stroke=\"%s\" class=\"node-box%s\"/>\n",
                cx, y, x + NODE_WIDTH, cy, cx, y + NODE_HEIGHT, x, cy, color, isActive ? " node-active" : ""));
        svg.append(String.format(
                "<text x=\"%d\" y=\"%d\" text-anchor=\"middle\" class=\"node-text\">%s</text>\n",
                cx, cy + 5, escapeText(name)));
    }

    /**
     * 绘制通用节点
     */
    private void drawGenericNode(StringBuilder svg, int x, int y, String name, int type, boolean isActive) {
        String color = isActive ? COLOR_ACTIVE : "#909399";
        svg.append(String.format(
                "<rect x=\"%d\" y=\"%d\" width=\"%d\" height=\"%d\" fill=\"#fff\" stroke=\"%s\" class=\"node-box%s\"/>\n",
                x, y, NODE_WIDTH, NODE_HEIGHT, color, isActive ? " node-active" : ""));
        svg.append(String.format(
                "<text x=\"%d\" y=\"%d\" text-anchor=\"middle\" class=\"node-text\">%s</text>\n",
                x + NODE_WIDTH / 2, y + NODE_HEIGHT / 2 + 5, escapeText(name)));
    }

    /**
     * 绘制连线
     */
    private void drawLine(StringBuilder svg, int x1, int y1, int x2, int y2, boolean isActive) {
        String color = isActive ? COLOR_ACTIVE : COLOR_LINE;
        svg.append(String.format(
                "<line x1=\"%d\" y1=\"%d\" x2=\"%d\" y2=\"%d\" stroke=\"%s\" class=\"line\"/>\n",
                x1 + NODE_WIDTH / 2, y1, x2 + NODE_WIDTH / 2, y2, color));
        // 绘制箭头
        svg.append(String.format(
                "<polygon points=\"%d,%d %d,%d %d,%d\" class=\"arrow\" fill=\"%s\"/>\n",
                x2 + NODE_WIDTH / 2 - 6, y2 - 10,
                x2 + NODE_WIDTH / 2 + 6, y2 - 10,
                x2 + NODE_WIDTH / 2, y2, color));
    }

    /**
     * 绘制分支连线（带条件标签）
     */
    private void drawBranchLine(StringBuilder svg, int x1, int y1, int x2, int y2, String condition, boolean isActive) {
        String color = isActive ? COLOR_ACTIVE : COLOR_LINE;
        // 绘制折线
        int midY = y1 + (y2 - y1) / 2;
        svg.append(String.format(
                "<path d=\"M %d %d L %d %d L %d %d\" stroke=\"%s\" class=\"line\"/>\n",
                x1 + NODE_WIDTH / 2, y1,
                x2 + NODE_WIDTH / 2, midY,
                x2 + NODE_WIDTH / 2, y2, color));
        // 绘制箭头
        svg.append(String.format(
                "<polygon points=\"%d,%d %d,%d %d,%d\" fill=\"%s\"/>\n",
                x2 + NODE_WIDTH / 2 - 6, y2 - 10,
                x2 + NODE_WIDTH / 2 + 6, y2 - 10,
                x2 + NODE_WIDTH / 2, y2, color));
        // 绘制条件标签
        if (condition != null && !condition.isEmpty()) {
            svg.append(String.format(
                    "<text x=\"%d\" y=\"%d\" text-anchor=\"middle\" font-size=\"12\" fill=\"#606266\">%s</text>\n",
                    x2 + NODE_WIDTH / 2, midY - 10, escapeText(condition)));
        }
    }

    /**
     * 获取候选人文本描述
     */
    private String getCandidateText(NodeModel node) {
        Map<String, Object> extendConfig = node.getExtendConfig();
        if (extendConfig == null) return "请设置审批人";

        Object strategy = extendConfig.get("candidateStrategy");
        if (strategy == null) return "请设置审批人";

        int strategyCode = ((Number) strategy).intValue();
        Map<Integer, String> strategyLabels = new HashMap<>();
        strategyLabels.put(10, "指定成员");
        strategyLabels.put(20, "指定角色");
        strategyLabels.put(30, "指定部门");
        strategyLabels.put(40, "部门负责人");
        strategyLabels.put(50, "发起人自己");
        strategyLabels.put(60, "发起人部门负责人");
        strategyLabels.put(70, "发起人上级领导");
        strategyLabels.put(80, "发起人自选");
        strategyLabels.put(90, "审批人自选");
        strategyLabels.put(100, "表达式");

        return strategyLabels.getOrDefault(strategyCode, "未设置");
    }

    /**
     * 获取抄送人文本描述
     */
    private String getCcText(NodeModel node) {
        Map<String, Object> extendConfig = node.getExtendConfig();
        if (extendConfig == null) return "请设置抄送人";

        Object ccUserIds = extendConfig.get("ccUserIds");
        if (ccUserIds == null) return "请设置抄送人";

        if (ccUserIds instanceof List) {
            int count = ((List<?>) ccUserIds).size();
            return "抄送 " + count + " 人";
        }
        return "请设置抄送人";
    }

    /**
     * 转义文本中的特殊字符
     */
    private String escapeText(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    /**
     * 节点位置记录
     */
    private static class NodePosition {
        int x;
        int y;

        NodePosition(int x, int y) {
            this.x = x;
            this.y = y;
        }
    }

    /**
     * 画布布局信息
     */
    private static class CanvasLayout {
        int maxX = 0;
        int maxY = 0;
        Map<String, NodePosition> nodePositions;
    }
}