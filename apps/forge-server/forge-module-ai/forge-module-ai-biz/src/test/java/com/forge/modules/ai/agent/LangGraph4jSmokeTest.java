package com.forge.modules.ai.agent;

import org.bsc.langgraph4j.CompileConfig;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphInput;
import org.bsc.langgraph4j.NodeOutput;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.action.AsyncEdgeAction;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.action.InterruptibleAction;
import org.bsc.langgraph4j.action.InterruptionMetadata;
import org.bsc.langgraph4j.checkpoint.MemorySaver;
import org.bsc.langgraph4j.state.AgentState;
import org.junit.jupiter.api.Test;

import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

/**
 * LangGraph4j v1.9.2 API 冒烟：条件循环 + interrupt 暂停/恢复 + checkpoint 闭环
 */
class LangGraph4jSmokeTest {

    static class SmokeState extends AgentState {
        SmokeState(Map<String, Object> data) { super(data); }
    }

    @Test
    void interruptAndResumeFlow() throws Exception {
        MemorySaver saver = new MemorySaver();

        StateGraph<SmokeState> graph = new StateGraph<>(SmokeState::new)
                .addNode("prepare", AsyncNodeAction.node_async(state ->
                        Map.of("prepared", true)))
                .addNode("approval", new ApprovalNode())
                .addNode("finish", AsyncNodeAction.node_async(state ->
                        Map.of("finalAnswer", "done:" + state.value("decision").orElse("none"))))
                .addEdge(StateGraph.START, "prepare")
                .addEdge("prepare", "approval")
                .addEdge("approval", "finish")
                .addEdge("finish", StateGraph.END);

        CompiledGraph<SmokeState> app =
                graph.compile(CompileConfig.builder()
                        .checkpointSaver(saver)
                        .interruptBefore("approval")
                        .build());

        RunnableConfig config = RunnableConfig.builder().threadId("t-1").build();

        // 第一段：在 approval 前 interrupt 暂停
        Iterator<NodeOutput<SmokeState>> first = app.stream(
                GraphInput.args(Map.of("input", "hello")), config).iterator();
        String lastNode = null;
        while (first.hasNext()) {
            lastNode = first.next().node();
        }
        assertEquals("prepare", lastNode);
        Optional<org.bsc.langgraph4j.checkpoint.Checkpoint> cp = saver.get(config);
        assertTrue(cp.isPresent());

        // 恢复：注入决策，跑完剩余节点
        Iterator<NodeOutput<SmokeState>> second = app.stream(
                GraphInput.resume(Map.of("decision", "APPROVED")), config).iterator();
        String finalAnswer = null;
        while (second.hasNext()) {
            NodeOutput<SmokeState> out = second.next();
            finalAnswer = out.state().value("finalAnswer").map(String::valueOf).orElse(finalAnswer);
        }
        assertEquals("done:APPROVED", finalAnswer);
    }

    static class ApprovalNode implements AsyncNodeAction<SmokeState>, InterruptibleAction<SmokeState> {
        @Override
        public CompletableFuture<Map<String, Object>> apply(SmokeState state) {
            return CompletableFuture.completedFuture(Map.of("approved", true));
        }

        @Override
        public Optional<InterruptionMetadata<SmokeState>> interrupt(String reason, SmokeState state) {
            if (state.value("decision").isPresent()) {
                return Optional.empty();
            }
            return Optional.of(InterruptionMetadata.builder("approval", state)
                    .reason("是否批准该操作？")
                    .build());
        }
    }

    @Test
    void conditionalLoopFlow() throws Exception {
        StateGraph<SmokeState> graph = new StateGraph<>(SmokeState::new)
                .addNode("counter", AsyncNodeAction.node_async(state ->
                        Map.of("count", state.<Integer>value("count").orElse(0) + 1)))
                .addConditionalEdges("counter",
                        AsyncEdgeAction.edge_async(state ->
                                state.<Integer>value("count").orElse(0) >= 3 ? "done" : "loop"),
                        Map.of("done", StateGraph.END, "loop", "counter"))
                .addEdge(StateGraph.START, "counter");

        CompiledGraph<SmokeState> app = graph.compile();
        Optional<SmokeState> result = app.invoke(Map.of(), RunnableConfig.builder().threadId("loop-1").build());
        assertEquals(3, result.map(s -> s.<Integer>value("count").orElse(-1)).orElse(-1));
    }
}
