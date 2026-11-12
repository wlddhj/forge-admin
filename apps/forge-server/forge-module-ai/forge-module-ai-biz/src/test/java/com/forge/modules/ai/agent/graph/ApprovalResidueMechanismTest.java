package com.forge.modules.ai.agent.graph;

import com.forge.modules.ai.agent.kernel.AgentGraphState;
import org.bsc.langgraph4j.CompileConfig;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphInput;
import org.bsc.langgraph4j.NodeOutput;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.action.AsyncEdgeAction;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.checkpoint.MemorySaver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 审批残留清空机制守护测试（安全堵口第一批 T6）。
 * 守护 ConversationalAssistantGraph 两处修复依赖的框架语义与路由语义：
 * 1. tools 节点返回含 null 值的 Map 后，APPROVAL 在 state 中视为缺失（langgraph4j 浅覆盖）；
 * 2. decisionRouter 精确匹配 "APPROVED"：REJECTED 残留不得放行高危调用。
 * （本类用最小图复刻业务节点的返回/路由语义——若业务图行为变化请同步此处。）
 */
class ApprovalResidueMechanismTest {

    /** 复刻业务 decisionRouter 的精确匹配 + 高危判定语义 */
    private static String route(AgentGraphState state) {
        boolean approved = "APPROVED".equals(state.value(AgentGraphState.APPROVAL)
                .map(String::valueOf).orElse(""));
        boolean highRisk = Boolean.TRUE.equals(state.value("highRisk").orElse(false));
        if (highRisk && !approved) {
            return "approval";
        }
        return "tools";
    }

    private CompiledGraph<AgentGraphState> buildApp() throws Exception {
        StateGraph<AgentGraphState> graph = new StateGraph<>(AgentGraphState::new)
                .addNode("decision", AsyncNodeAction.node_async(state -> {
                    // 每轮都判定为高危调用（模拟模型持续发起高危工具请求）
                    Map<String, Object> update = new HashMap<>();
                    update.put("round", (Integer) state.value("round").orElse(0) + 1);
                    update.put("highRisk", true);
                    return update;
                }))
                .addNode("approval", org.bsc.langgraph4j.action.AsyncNodeActionWithConfig.node_async(
                        (org.bsc.langgraph4j.action.NodeActionWithConfig<AgentGraphState>) (state, config) -> {
                            if (state.value(AgentGraphState.APPROVAL).isEmpty()) {
                                throw new org.bsc.langgraph4j.GraphInterruptException(config, "等待人工确认");
                            }
                            return Map.of();
                        }))
                .addNode("tools", AsyncNodeAction.node_async(state -> {
                    // 复刻业务修复：执行完成后显式清空 APPROVAL（null 值覆盖）
                    Map<String, Object> update = new HashMap<>();
                    update.put("executed", true);
                    update.put(AgentGraphState.APPROVAL, null);
                    return update;
                }))
                .addNode("summary", AsyncNodeAction.node_async(state -> Map.of(
                        AgentGraphState.FINAL_ANSWER, "该请求包含高危操作，已被人工审核驳回，未执行。")))
                .addEdge(StateGraph.START, "decision")
                .addConditionalEdges("decision", AsyncEdgeAction.edge_async(
                        ApprovalResidueMechanismTest::route),
                        Map.of("approval", "approval", "tools", "tools"))
                // 复刻业务图：审批节点对 REJECTED 结论分流到总结（拒绝文案），APPROVED/其他非空结论放行 tools
                .addConditionalEdges("approval", AsyncEdgeAction.edge_async(state ->
                        "REJECTED".equals(state.value(AgentGraphState.APPROVAL)
                                .map(String::valueOf).orElse("")) ? "summary" : "tools"),
                        Map.of("summary", "summary", "tools", "tools"))
                .addEdge("tools", "decision")
                .addEdge("summary", StateGraph.END);

        return graph.compile(CompileConfig.builder()
                .checkpointSaver(new MemorySaver())
                .build());
    }

    @Test
    @DisplayName("残留 APPROVED 只放行第一次：tools 执行清空后，第二次高危调用再次中断")
    void approvalShouldBeConsumedAfterToolsRun() throws Exception {
        CompiledGraph<AgentGraphState> app = buildApp();
        RunnableConfig config = RunnableConfig.builder().threadId("t-approve").build();

        // 初始注入"已批准"残留（等价于上一轮批准后的 state），图自行驱动：
        // decision(高危) → route 放行(tools) → tools 清空 APPROVAL → decision(再次高危) → 中断（最后输出为前驱 decision）
        Iterator<NodeOutput<AgentGraphState>> it =
                app.stream(GraphInput.args(Map.of(AgentGraphState.APPROVAL, "APPROVED")), config).iterator();
        String lastNode = drain(it);
        assertEquals("decision", lastNode,
                "APPROVAL 应在 tools 执行后被消费清空，第二次高危调用必须重新人工确认");
    }

    @Test
    @DisplayName("REJECTED 结论分流到总结拒绝文案，绝不进入工具执行")
    void rejectedShouldRouteToSummaryNotTools() throws Exception {
        CompiledGraph<AgentGraphState> app = buildApp();
        RunnableConfig config = RunnableConfig.builder().threadId("t-reject").build();

        // REJECTED 结论：decision(高危) → route 不放行 → approval(非空放行) → 分流 summary → END
        Iterator<NodeOutput<AgentGraphState>> it =
                app.stream(GraphInput.args(Map.of(AgentGraphState.APPROVAL, "REJECTED")), config).iterator();
        String finalAnswer = null;
        boolean toolsExecuted = false;
        while (it.hasNext()) {
            NodeOutput<AgentGraphState> out = it.next();
            finalAnswer = out.state().value(AgentGraphState.FINAL_ANSWER)
                    .map(String::valueOf).orElse(finalAnswer);
            toolsExecuted = toolsExecuted
                    || Boolean.TRUE.equals(out.state().value("executed").orElse(false));
        }
        assertTrue(finalAnswer != null && finalAnswer.contains("驳回"),
                "REJECTED 必须走拒绝总结文案");
        assertFalse(toolsExecuted, "REJECTED 绝不得进入工具执行");
    }

    @Test
    @DisplayName("null 清除后 state.value 返回 empty（框架语义前提）")
    void nullOverrideRemovesValue() throws Exception {
        StateGraph<AgentGraphState> mini = new StateGraph<>(AgentGraphState::new)
                .addNode("put", AsyncNodeAction.node_async(state -> Map.of(
                        AgentGraphState.APPROVAL, "APPROVED")))
                .addNode("clear", AsyncNodeAction.node_async(state -> {
                    Map<String, Object> update = new HashMap<>();
                    update.put(AgentGraphState.APPROVAL, null);
                    return update;
                }))
                .addEdge(StateGraph.START, "put")
                .addEdge("put", "clear")
                .addEdge("clear", StateGraph.END);
        CompiledGraph<AgentGraphState> app = mini.compile();
        Iterator<NodeOutput<AgentGraphState>> it =
                app.stream(GraphInput.args(Map.of()), RunnableConfig.builder().threadId("t-null").build()).iterator();
        AgentGraphState finalState = null;
        while (it.hasNext()) {
            finalState = it.next().state();
        }
        assertTrue(finalState.value(AgentGraphState.APPROVAL).isEmpty(),
                "null 值覆盖后 value() 必须为 empty——这是 tools 清空审批的机制前提");
        assertFalse("APPROVED".equals(finalState.value(AgentGraphState.APPROVAL).map(String::valueOf).orElse("")));
    }

    private String drain(Iterator<NodeOutput<AgentGraphState>> iterator) {
        String last = null;
        while (iterator.hasNext()) {
            NodeOutput<AgentGraphState> out = iterator.next();
            last = out.node();
        }
        return last;
    }
}
