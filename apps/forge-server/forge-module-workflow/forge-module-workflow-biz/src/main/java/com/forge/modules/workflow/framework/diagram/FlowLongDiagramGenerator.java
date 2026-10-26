package com.forge.modules.workflow.framework.diagram;

import com.aizuda.bpm.engine.core.enums.TaskType;
import com.aizuda.bpm.engine.model.ConditionNode;
import com.aizuda.bpm.engine.model.NodeModel;
import com.aizuda.bpm.engine.model.ProcessModel;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * FlowLong 流程图生成器
 * 采用层级布局算法，确保节点不重叠
 *
 * @author forge-admin
 */
@Slf4j
@Component
public class FlowLongDiagramGenerator {
    private static final Logger log = LoggerFactory.getLogger(FlowLongDiagramGenerator.class);

    private final ObjectMapper objectMapper;

    // 节点尺寸常量（适当缩小）
    private static final int NODE_WIDTH = 100;
    private static final int NODE_HEIGHT = 40;
    private static final int START_END_RADIUS = 18;
    private static final int HORIZONTAL_GAP = 50;
    private static final int VERTICAL_GAP = 60;
    /** 画布四周留白（兼作坐标归一化的左/上边界） */
    private static final int MARGIN = 40;

    public FlowLongDiagramGenerator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    // 颜色常量
    private static final String COLOR_START = "#1890ff";
    private static final String COLOR_APPROVAL = "#fa8c16";
    private static final String COLOR_CC = "#52c41a";
    private static final String COLOR_END = "#909399";
    private static final String COLOR_CONDITION = "#722ed1";
    private static final String COLOR_PARALLEL = "#13c2c2";
    private static final String COLOR_INCLUSIVE = "#eb2f96";
    private static final String COLOR_CALL_PROCESS = "#2f54eb";
    private static final String COLOR_TIMER = "#faad14";
    private static final String COLOR_TRIGGER = "#7cb305";
    private static final String COLOR_AUTO_PASS = "#52c41a";
    private static final String COLOR_AUTO_REJECT = "#f5222d";
    private static final String COLOR_ROUTE = "#1890ff";
    private static final String COLOR_ACTIVE = "#409eff";
    private static final String COLOR_LINE = "#bfbfbf";

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

    private InputStream generateEmptyDiagram() {
        String svg = "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"400\" height=\"200\">" +
                "<text x=\"200\" y=\"100\" text-anchor=\"middle\" fill=\"#909399\" font-size=\"14\">流程模型加载失败</text>" +
                "</svg>";
        return new ByteArrayInputStream(svg.getBytes(StandardCharsets.UTF_8));
    }

    private String generateSvg(ProcessModel processModel, Set<String> activeNodes) {
        // 收集所有节点，构建坐标映射
        Map<String, Point> nodePositions = new LinkedHashMap<>();
        List<Connection> connections = new ArrayList<>();

        // 递归遍历节点，计算位置
        traverseAndPosition(processModel.getNodeConfig(), 0, 0, nodePositions, connections, null, null);

        // 坐标归一化：分支以网关为中心横向展开，网关靠左时分支 x 为负；
        // SVG 无 viewBox，负坐标节点会被画布左缘裁掉——整体平移到留边距的非负区间
        int minX = Integer.MAX_VALUE;
        for (Point p : nodePositions.values()) {
            minX = Math.min(minX, p.x);
        }
        if (minX < MARGIN) {
            int shift = MARGIN - minX;
            for (Point p : nodePositions.values()) {
                p.x += shift;
            }
        }

        // 合成统一结束节点：所有链尾（无 childNode 且自身非分支容器/显式结束节点）汇聚到唯一"结束"
        // 模型任一链尾已显式放置结束节点时不再合成，尊重建模者的画法
        List<Point> terminalPoints = new ArrayList<>();
        boolean hasExplicitEnd = false;
        for (Map.Entry<String, Point> entry : nodePositions.entrySet()) {
            NodeModel n = findNodeByKey(processModel.getNodeConfig(), entry.getKey());
            if (n == null || n.getChildNode() != null || hasBranches(n)) {
                continue;
            }
            if (TaskType.end.eq(n.getType())) {
                hasExplicitEnd = true;
                continue;
            }
            terminalPoints.add(entry.getValue());
        }
        Point endPoint = null;
        if (!hasExplicitEnd && !terminalPoints.isEmpty()) {
            int terminalMaxY = terminalPoints.stream().mapToInt(p -> p.y).max().orElse(0);
            int minCx = terminalPoints.stream().mapToInt(p -> p.x + NODE_WIDTH / 2).min().orElse(0);
            int maxCx = terminalPoints.stream().mapToInt(p -> p.x + NODE_WIDTH / 2).max().orElse(0);
            endPoint = new Point((minCx + maxCx) / 2 - NODE_WIDTH / 2,
                    terminalMaxY + NODE_HEIGHT + VERTICAL_GAP);
        }

        // 计算画布尺寸
        int maxX = 0, maxY = 0;
        for (Point p : nodePositions.values()) {
            maxX = Math.max(maxX, p.x + NODE_WIDTH);
            maxY = Math.max(maxY, p.y + NODE_HEIGHT);
        }
        if (endPoint != null) {
            maxX = Math.max(maxX, endPoint.x + NODE_WIDTH);
            maxY = Math.max(maxY, endPoint.y + 2 * START_END_RADIUS + 10);
        }
        int canvasWidth = Math.max(maxX + MARGIN, 400);
        int canvasHeight = Math.max(maxY + MARGIN, 300);

        StringBuilder svg = new StringBuilder();
        svg.append(String.format("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"%d\" height=\"%d\">\n",
                canvasWidth, canvasHeight));
        svg.append(generateStyles());

        // 先绘制连线
        for (Connection conn : connections) {
            Point from = nodePositions.get(conn.fromKey);
            Point to = nodePositions.get(conn.toKey);
            if (from != null && to != null) {
                boolean isActive = activeNodes.contains(conn.fromKey) || activeNodes.contains(conn.toKey);
                drawConnection(svg, from, to, conn.label, isActive);
            }
        }

        // 再绘制节点
        for (Map.Entry<String, Point> entry : nodePositions.entrySet()) {
            String nodeKey = entry.getKey();
            Point pos = entry.getValue();
            NodeModel node = findNodeByKey(processModel.getNodeConfig(), nodeKey);
            if (node != null) {
                boolean isActive = activeNodes.contains(nodeKey);
                drawNode(svg, node, pos.x, pos.y, isActive);
            }
        }

        // 最后绘制合成结束节点（连线先于节点，保持与主流程绘制顺序一致）
        if (endPoint != null) {
            for (Point terminal : terminalPoints) {
                drawConnection(svg, terminal, endPoint, null, false);
            }
            drawEndNode(svg, endPoint.x, endPoint.y, "结束", "");
        }

        svg.append("</svg>");
        return svg.toString();
    }

    private String generateStyles() {
        return "<defs>\n" +
                "<marker id=\"arrow\" markerWidth=\"8\" markerHeight=\"8\" refX=\"7\" refY=\"3\" orient=\"auto\">\n" +
                "<path d=\"M0,0 L0,6 L8,3 z\" fill=\"#bfbfbf\"/>\n" +
                "</marker>\n" +
                "<marker id=\"arrow-active\" markerWidth=\"8\" markerHeight=\"8\" refX=\"7\" refY=\"3\" orient=\"auto\">\n" +
                "<path d=\"M0,0 L0,6 L8,3 z\" fill=\"#409eff\"/>\n" +
                "</marker>\n" +
                "<style>\n" +
                ".node-box { stroke-width: 1.5; }\n" +
                ".node-active { stroke: #409eff; stroke-width: 2; }\n" +
                ".node-text { font-size: 12px; font-family: Arial, sans-serif; fill: #303133; }\n" +
                ".node-desc { font-size: 10px; fill: #909399; }\n" +
                ".branch-label { font-size: 11px; fill: #606266; }\n" +
                ".line { stroke: #bfbfbf; stroke-width: 1.5; fill: none; marker-end: url(#arrow); }\n" +
                ".line-active { stroke: #409eff; stroke-width: 1.5; fill: none; marker-end: url(#arrow-active); }\n" +
                "</style>\n" +
                "</defs>\n";
    }

    // 坐标点
    private static class Point {
        int x, y;
        Point(int x, int y) { this.x = x; this.y = y; }
    }

    // 连线信息
    private static class Connection {
        String fromKey, toKey, label;
        Connection(String fromKey, String toKey, String label) {
            this.fromKey = fromKey;
            this.toKey = toKey;
            this.label = label;
        }
    }

    /**
     * 递归遍历并计算节点位置
     */
    private void traverseAndPosition(NodeModel node, int x, int y,
                                      Map<String, Point> positions, List<Connection> connections,
                                      String parentKey, String branchLabel) {
        if (node == null) return;

        String nodeKey = node.getNodeKey();

        // 如果节点已存在，不重复添加（避免分支汇聚后的重复）
        if (positions.containsKey(nodeKey)) {
            // 添加连线到已存在的节点
            if (parentKey != null) {
                connections.add(new Connection(parentKey, nodeKey, branchLabel));
            }
            return;
        }

        // 记录节点位置
        positions.put(nodeKey, new Point(x, y));

        // 添加连线
        if (parentKey != null) {
            connections.add(new Connection(parentKey, nodeKey, branchLabel));
        }

        int nextY = y + NODE_HEIGHT + VERTICAL_GAP;

        // 检查是否有分支（条件、并行、包容、路由）
        boolean hasBranches = hasBranches(node);

        if (hasBranches) {
            // 分支节点：横向展开子分支
            List<BranchData> branches = collectBranches(node);
            int branchCount = branches.size();
            int totalWidth = branchCount * NODE_WIDTH + (branchCount - 1) * HORIZONTAL_GAP;
            int startX = x + NODE_WIDTH / 2 - totalWidth / 2;

            for (int i = 0; i < branches.size(); i++) {
                BranchData branch = branches.get(i);
                int branchX = startX + i * (NODE_WIDTH + HORIZONTAL_GAP);

                if (branch.childNode != null) {
                    traverseAndPosition(branch.childNode, branchX, nextY, positions, connections,
                            nodeKey, branch.label);
                }
            }

            // 分支汇聚后，继续处理 childNode（主干继续）
            if (node.getChildNode() != null) {
                // 汇聚点位置：在分支下方，居中
                int mergeY = nextY + calculateBranchDepth(node) * (NODE_HEIGHT + VERTICAL_GAP);
                traverseAndPosition(node.getChildNode(), x, mergeY, positions, connections,
                        getLastBranchNodeKey(node), null);
            }
        } else {
            // 普通节点：继续处理 childNode
            if (node.getChildNode() != null) {
                traverseAndPosition(node.getChildNode(), x, nextY, positions, connections, nodeKey, null);
            }
        }
    }

    /**
     * 计算分支的最大深度
     */
    private int calculateBranchDepth(NodeModel branchNode) {
        int maxDepth = 1;
        List<BranchData> branches = collectBranches(branchNode);
        for (BranchData bd : branches) {
            if (bd.childNode != null) {
                int depth = getNodeDepth(bd.childNode);
                maxDepth = Math.max(maxDepth, depth);
            }
        }
        return maxDepth;
    }

    /**
     * 计算节点深度
     */
    private int getNodeDepth(NodeModel node) {
        if (node == null) return 0;
        int depth = 1;
        if (hasBranches(node)) {
            depth += calculateBranchDepth(node);
            if (node.getChildNode() != null) {
                depth += getNodeDepth(node.getChildNode());
            }
        } else if (node.getChildNode() != null) {
            depth += getNodeDepth(node.getChildNode());
        }
        return depth;
    }

    /**
     * 获取分支最后一个节点的 key（用于汇聚连线）
     */
    private String getLastBranchNodeKey(NodeModel branchNode) {
        List<BranchData> branches = collectBranches(branchNode);
        for (BranchData bd : branches) {
            if (bd.childNode != null) {
                NodeModel last = getLastNode(bd.childNode);
                if (last != null) return last.getNodeKey();
            }
        }
        return branchNode.getNodeKey();
    }

    /**
     * 获取链路上最后一个节点
     */
    private NodeModel getLastNode(NodeModel node) {
        if (node == null) return null;
        if (hasBranches(node) && node.getChildNode() != null) {
            return getLastNode(node.getChildNode());
        }
        if (node.getChildNode() != null) {
            return getLastNode(node.getChildNode());
        }
        return node;
    }

    /**
     * 判断节点是否有分支
     */
    private boolean hasBranches(NodeModel node) {
        return (node.getConditionNodes() != null && !node.getConditionNodes().isEmpty())
                || (node.getParallelNodes() != null && !node.getParallelNodes().isEmpty())
                || (node.getInclusiveNodes() != null && !node.getInclusiveNodes().isEmpty())
                || (node.getRouteNodes() != null && !node.getRouteNodes().isEmpty());
    }

    // 分支数据
    private static class BranchData {
        String label;
        NodeModel childNode;
        BranchData(String label, NodeModel childNode) {
            this.label = label;
            this.childNode = childNode;
        }
    }

    /**
     * 收集所有分支数据
     */
    private List<BranchData> collectBranches(NodeModel node) {
        List<BranchData> branches = new ArrayList<>();

        if (node.getConditionNodes() != null) {
            for (ConditionNode cn : node.getConditionNodes()) {
                branches.add(new BranchData(cn.getNodeName(), cn.getChildNode()));
            }
        }
        if (node.getParallelNodes() != null) {
            for (ConditionNode pn : node.getParallelNodes()) {
                branches.add(new BranchData(pn.getNodeName(), pn.getChildNode()));
            }
        }
        if (node.getInclusiveNodes() != null) {
            for (ConditionNode in : node.getInclusiveNodes()) {
                branches.add(new BranchData(in.getNodeName(), in.getChildNode()));
            }
        }
        if (node.getRouteNodes() != null) {
            for (ConditionNode rn : node.getRouteNodes()) {
                branches.add(new BranchData(rn.getNodeName(), rn.getChildNode()));
            }
        }
        return branches;
    }

    /**
     * 根据 key 查找节点
     */
    private NodeModel findNodeByKey(NodeModel root, String key) {
        if (root == null) return null;
        if (root.getNodeKey().equals(key)) return root;

        // 搜索子节点
        NodeModel found = findNodeByKey(root.getChildNode(), key);
        if (found != null) return found;

        // 搜索分支子节点
        List<BranchData> branches = collectBranches(root);
        for (BranchData bd : branches) {
            found = findNodeByKey(bd.childNode, key);
            if (found != null) return found;
        }
        return null;
    }

    /**
     * 绘制连线
     */
    private void drawConnection(StringBuilder svg, Point from, Point to, String label, boolean isActive) {
        int x1 = from.x + NODE_WIDTH / 2;
        int y1 = from.y + NODE_HEIGHT;
        int x2 = to.x + NODE_WIDTH / 2;
        int y2 = to.y;

        String lineClass = isActive ? "line-active" : "line";

        // 绘制直线或折线（根据是否需要分支标签）
        if (label != null && !label.isEmpty()) {
            // 有分支标签，绘制折线并在中间显示标签
            int midY = (y1 + y2) / 2;
            svg.append(String.format(
                    "<path d=\"M %d %d L %d %d L %d %d\" class=\"%s\"/>\n",
                    x1, y1, x1, midY, x2, y2, lineClass));
            svg.append(String.format(
                    "<text x=\"%d\" y=\"%d\" text-anchor=\"middle\" class=\"branch-label\" fill=\"#909399\">%s</text>\n",
                    (x1 + x2) / 2, midY - 5, escapeText(label)));
        } else {
            // 无标签，绘制直线
            svg.append(String.format(
                    "<line x1=\"%d\" y1=\"%d\" x2=\"%d\" y2=\"%d\" class=\"%s\"/>\n",
                    x1, y1, x2, y2, lineClass));
        }
    }

    /**
     * 绘制节点
     */
    private void drawNode(StringBuilder svg, NodeModel node, int x, int y, boolean isActive) {
        Integer type = node.getType();
        if (type == null) type = TaskType.approval.getValue();

        String activeClass = isActive ? " node-active" : "";

        if (TaskType.major.eq(type)) {
            // 发起人节点（开始）
            drawStartNode(svg, x, y, node.getNodeName(), activeClass);
        } else if (TaskType.end.eq(type)) {
            // 结束节点
            drawEndNode(svg, x, y, node.getNodeName(), activeClass);
        } else if (TaskType.approval.eq(type)) {
            // 审批节点
            drawApprovalNode(svg, x, y, node.getNodeName(), activeClass);
        } else if (TaskType.cc.eq(type)) {
            // 抄送节点
            drawCcNode(svg, x, y, node.getNodeName(), activeClass);
        } else if (TaskType.conditionBranch.eq(type) || TaskType.conditionNode.eq(type)) {
            // 条件分支节点
            drawConditionNode(svg, x, y, node.getNodeName(), activeClass);
        } else if (TaskType.parallelBranch.eq(type)) {
            // 并行分支节点
            drawParallelNode(svg, x, y, node.getNodeName(), activeClass);
        } else if (TaskType.inclusiveBranch.eq(type)) {
            // 包容分支节点
            drawInclusiveNode(svg, x, y, node.getNodeName(), activeClass);
        } else if (TaskType.callProcess.eq(type)) {
            // 子流程节点
            drawCallProcessNode(svg, x, y, node.getNodeName(), activeClass);
        } else if (TaskType.timer.eq(type)) {
            // 定时器节点
            drawTimerNode(svg, x, y, node.getNodeName(), activeClass);
        } else if (TaskType.trigger.eq(type)) {
            // 触发器节点
            drawTriggerNode(svg, x, y, node.getNodeName(), activeClass);
        } else if (TaskType.routeBranch.eq(type)) {
            // 路由分支节点
            drawRouteNode(svg, x, y, node.getNodeName(), activeClass);
        } else if (TaskType.autoPass.eq(type)) {
            // 自动通过节点
            drawAutoPassNode(svg, x, y, node.getNodeName(), activeClass);
        } else if (TaskType.autoReject.eq(type)) {
            // 自动拒绝节点
            drawAutoRejectNode(svg, x, y, node.getNodeName(), activeClass);
        } else {
            // 默认节点
            drawGenericNode(svg, x, y, node.getNodeName(), activeClass);
        }
    }

    // ========== 节点绘制方法 ==========

    private void drawStartNode(StringBuilder svg, int x, int y, String name, String activeClass) {
        int cx = x + NODE_WIDTH / 2;
        int cy = y + START_END_RADIUS + 5;
        svg.append(String.format(
                "<circle cx=\"%d\" cy=\"%d\" r=\"%d\" fill=\"#e6f7ff\" stroke=\"%s\" class=\"node-box%s\"/>\n",
                cx, cy, START_END_RADIUS, COLOR_START, activeClass));
        svg.append(String.format(
                "<text x=\"%d\" y=\"%d\" text-anchor=\"middle\" class=\"node-text\">%s</text>\n",
                cx, cy + 4, escapeText(name)));
    }

    private void drawEndNode(StringBuilder svg, int x, int y, String name, String activeClass) {
        int cx = x + NODE_WIDTH / 2;
        int cy = y + START_END_RADIUS + 5;
        svg.append(String.format(
                "<circle cx=\"%d\" cy=\"%d\" r=\"%d\" fill=\"%s\" stroke=\"%s\" class=\"node-box%s\"/>\n",
                cx, cy, START_END_RADIUS, COLOR_END, COLOR_END, activeClass));
        svg.append(String.format(
                "<text x=\"%d\" y=\"%d\" text-anchor=\"middle\" class=\"node-text\" fill=\"#fff\">%s</text>\n",
                cx, cy + 4, escapeText(name)));
    }

    private void drawApprovalNode(StringBuilder svg, int x, int y, String name, String activeClass) {
        svg.append(String.format(
                "<rect x=\"%d\" y=\"%d\" width=\"%d\" height=\"%d\" rx=\"3\" fill=\"#fff\" stroke=\"%s\" class=\"node-box%s\"/>\n",
                x, y, NODE_WIDTH, NODE_HEIGHT, COLOR_APPROVAL, activeClass));
        svg.append(String.format(
                "<text x=\"%d\" y=\"%d\" text-anchor=\"middle\" class=\"node-text\">%s</text>\n",
                x + NODE_WIDTH / 2, y + NODE_HEIGHT / 2 + 4, escapeText(name)));
    }

    private void drawCcNode(StringBuilder svg, int x, int y, String name, String activeClass) {
        svg.append(String.format(
                "<rect x=\"%d\" y=\"%d\" width=\"%d\" height=\"%d\" rx=\"3\" fill=\"#f6ffed\" stroke=\"%s\" class=\"node-box%s\"/>\n",
                x, y, NODE_WIDTH, NODE_HEIGHT, COLOR_CC, activeClass));
        svg.append(String.format(
                "<text x=\"%d\" y=\"%d\" text-anchor=\"middle\" class=\"node-text\">%s</text>\n",
                x + NODE_WIDTH / 2, y + NODE_HEIGHT / 2 + 4, escapeText(name)));
    }

    private void drawConditionNode(StringBuilder svg, int x, int y, String name, String activeClass) {
        int cx = x + NODE_WIDTH / 2;
        int cy = y + NODE_HEIGHT / 2;
        svg.append(String.format(
                "<polygon points=\"%d,%d %d,%d %d,%d %d,%d\" fill=\"#f9f0ff\" stroke=\"%s\" class=\"node-box%s\"/>\n",
                cx, y + 3, x + NODE_WIDTH - 3, cy, cx, y + NODE_HEIGHT - 3, x + 3, cy, COLOR_CONDITION, activeClass));
        svg.append(String.format(
                "<text x=\"%d\" y=\"%d\" text-anchor=\"middle\" class=\"node-text\" font-size=\"11\">%s</text>\n",
                cx, cy + 3, escapeText(name)));
    }

    private void drawParallelNode(StringBuilder svg, int x, int y, String name, String activeClass) {
        svg.append(String.format(
                "<rect x=\"%d\" y=\"%d\" width=\"%d\" height=\"%d\" rx=\"3\" fill=\"#e6fffb\" stroke=\"%s\" class=\"node-box%s\"/>\n",
                x, y, NODE_WIDTH, NODE_HEIGHT, COLOR_PARALLEL, activeClass));
        svg.append(String.format(
                "<text x=\"%d\" y=\"%d\" text-anchor=\"middle\" class=\"node-text\">%s</text>\n",
                x + NODE_WIDTH / 2, y + NODE_HEIGHT / 2 + 4, escapeText(name)));
    }

    private void drawInclusiveNode(StringBuilder svg, int x, int y, String name, String activeClass) {
        svg.append(String.format(
                "<rect x=\"%d\" y=\"%d\" width=\"%d\" height=\"%d\" rx=\"3\" fill=\"#fff0f6\" stroke=\"%s\" class=\"node-box%s\"/>\n",
                x, y, NODE_WIDTH, NODE_HEIGHT, COLOR_INCLUSIVE, activeClass));
        svg.append(String.format(
                "<text x=\"%d\" y=\"%d\" text-anchor=\"middle\" class=\"node-text\">%s</text>\n",
                x + NODE_WIDTH / 2, y + NODE_HEIGHT / 2 + 4, escapeText(name)));
    }

    private void drawCallProcessNode(StringBuilder svg, int x, int y, String name, String activeClass) {
        svg.append(String.format(
                "<rect x=\"%d\" y=\"%d\" width=\"%d\" height=\"%d\" rx=\"3\" fill=\"#f0f5ff\" stroke=\"%s\" class=\"node-box%s\"/>\n",
                x, y, NODE_WIDTH, NODE_HEIGHT, COLOR_CALL_PROCESS, activeClass));
        svg.append(String.format(
                "<rect x=\"%d\" y=\"%d\" width=\"%d\" height=\"%d\" fill=\"none\" stroke=\"%s\" stroke-dasharray=\"3,3\"/>\n",
                x + 5, y + 5, NODE_WIDTH - 10, NODE_HEIGHT - 10, COLOR_CALL_PROCESS));
        svg.append(String.format(
                "<text x=\"%d\" y=\"%d\" text-anchor=\"middle\" class=\"node-text\" font-size=\"11\">%s</text>\n",
                x + NODE_WIDTH / 2, y + NODE_HEIGHT / 2 + 4, escapeText(name)));
    }

    private void drawTimerNode(StringBuilder svg, int x, int y, String name, String activeClass) {
        int cx = x + NODE_WIDTH / 2;
        int cy = y + NODE_HEIGHT / 2;
        int r = NODE_HEIGHT / 2 - 5;
        svg.append(String.format(
                "<circle cx=\"%d\" cy=\"%d\" r=\"%d\" fill=\"#fffbe6\" stroke=\"%s\" class=\"node-box%s\"/>\n",
                cx, cy, r, COLOR_TIMER, activeClass));
        svg.append(String.format(
                "<text x=\"%d\" y=\"%d\" text-anchor=\"middle\" class=\"node-text\" font-size=\"11\">%s</text>\n",
                cx, cy + 4, escapeText(name)));
    }

    private void drawTriggerNode(StringBuilder svg, int x, int y, String name, String activeClass) {
        svg.append(String.format(
                "<rect x=\"%d\" y=\"%d\" width=\"%d\" height=\"%d\" rx=\"3\" fill=\"#fcffe6\" stroke=\"%s\" class=\"node-box%s\"/>\n",
                x, y, NODE_WIDTH, NODE_HEIGHT, COLOR_TRIGGER, activeClass));
        svg.append(String.format(
                "<text x=\"%d\" y=\"%d\" text-anchor=\"middle\" class=\"node-text\" font-size=\"11\">%s</text>\n",
                x + NODE_WIDTH / 2, y + NODE_HEIGHT / 2 + 4, escapeText(name)));
    }

    private void drawRouteNode(StringBuilder svg, int x, int y, String name, String activeClass) {
        int cx = x + NODE_WIDTH / 2;
        int cy = y + NODE_HEIGHT / 2;
        svg.append(String.format(
                "<polygon points=\"%d,%d %d,%d %d,%d\" fill=\"#e6f7ff\" stroke=\"%s\" class=\"node-box%s\"/>\n",
                x + 8, cy, cx, y + 3, x + NODE_WIDTH - 8, cy, COLOR_ROUTE, activeClass));
        svg.append(String.format(
                "<polygon points=\"%d,%d %d,%d %d,%d\" fill=\"#e6f7ff\" stroke=\"%s\" class=\"node-box%s\"/>\n",
                cx, y + NODE_HEIGHT - 3, x + 8, cy, x + NODE_WIDTH - 8, cy, COLOR_ROUTE, activeClass));
        svg.append(String.format(
                "<text x=\"%d\" y=\"%d\" text-anchor=\"middle\" class=\"node-text\" font-size=\"10\">%s</text>\n",
                cx, cy + 3, escapeText(name)));
    }

    private void drawAutoPassNode(StringBuilder svg, int x, int y, String name, String activeClass) {
        svg.append(String.format(
                "<rect x=\"%d\" y=\"%d\" width=\"%d\" height=\"%d\" rx=\"3\" fill=\"#f6ffed\" stroke=\"%s\" class=\"node-box%s\"/>\n",
                x, y, NODE_WIDTH, NODE_HEIGHT, COLOR_AUTO_PASS, activeClass));
        int cx = x + NODE_WIDTH / 2;
        int cy = y + NODE_HEIGHT / 2;
        svg.append(String.format(
                "<path d=\"M %d %d L %d %d L %d %d\" stroke=\"%s\" stroke-width=\"2\" fill=\"none\"/>\n",
                cx - 8, cy, cx - 2, cy + 5, cx + 8, cy - 5, COLOR_AUTO_PASS));
        svg.append(String.format(
                "<text x=\"%d\" y=\"%d\" text-anchor=\"middle\" class=\"node-desc\">%s</text>\n",
                x + NODE_WIDTH / 2, y + NODE_HEIGHT + 12, escapeText(name)));
    }

    private void drawAutoRejectNode(StringBuilder svg, int x, int y, String name, String activeClass) {
        svg.append(String.format(
                "<rect x=\"%d\" y=\"%d\" width=\"%d\" height=\"%d\" rx=\"3\" fill=\"#fff2f0\" stroke=\"%s\" class=\"node-box%s\"/>\n",
                x, y, NODE_WIDTH, NODE_HEIGHT, COLOR_AUTO_REJECT, activeClass));
        int cx = x + NODE_WIDTH / 2;
        int cy = y + NODE_HEIGHT / 2;
        svg.append(String.format(
                "<line x1=\"%d\" y1=\"%d\" x2=\"%d\" y2=\"%d\" stroke=\"%s\" stroke-width=\"2\"/>\n",
                cx - 7, cy - 7, cx + 7, cy + 7, COLOR_AUTO_REJECT));
        svg.append(String.format(
                "<line x1=\"%d\" y1=\"%d\" x2=\"%d\" y2=\"%d\" stroke=\"%s\" stroke-width=\"2\"/>\n",
                cx + 7, cy - 7, cx - 7, cy + 7, COLOR_AUTO_REJECT));
        svg.append(String.format(
                "<text x=\"%d\" y=\"%d\" text-anchor=\"middle\" class=\"node-desc\">%s</text>\n",
                x + NODE_WIDTH / 2, y + NODE_HEIGHT + 12, escapeText(name)));
    }

    private void drawGenericNode(StringBuilder svg, int x, int y, String name, String activeClass) {
        svg.append(String.format(
                "<rect x=\"%d\" y=\"%d\" width=\"%d\" height=\"%d\" rx=\"3\" fill=\"#fafafa\" stroke=\"#d9d9d9\" class=\"node-box%s\"/>\n",
                x, y, NODE_WIDTH, NODE_HEIGHT, activeClass));
        svg.append(String.format(
                "<text x=\"%d\" y=\"%d\" text-anchor=\"middle\" class=\"node-text\">%s</text>\n",
                x + NODE_WIDTH / 2, y + NODE_HEIGHT / 2 + 4, escapeText(name)));
    }

    private String escapeText(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}