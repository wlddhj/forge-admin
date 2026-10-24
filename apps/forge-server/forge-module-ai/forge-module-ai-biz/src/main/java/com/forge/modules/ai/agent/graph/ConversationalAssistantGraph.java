package com.forge.modules.ai.agent.graph;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.forge.common.exception.BusinessException;
import com.forge.modules.ai.agent.AgentEvents;
import com.forge.modules.ai.agent.kernel.AgentGraphContext;
import com.forge.modules.ai.agent.kernel.AgentGraphDefinition;
import com.forge.modules.ai.agent.kernel.AgentGraphState;
import com.forge.modules.ai.client.LlmClient;
import com.forge.modules.ai.client.NativeToolCalling;
import com.forge.modules.ai.dto.request.ChatRequest;
import com.forge.modules.ai.dto.response.ChatResponse;
import com.forge.modules.ai.entity.AiModelConfig;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.action.AsyncEdgeAction;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 内置对话 Agent 编排图：图片理解 → 工具决策(循环) → [高危确认] → 工具并行执行 → 总结回答
 */
@Component
@Slf4j
public class ConversationalAssistantGraph implements AgentGraphDefinition {

    public static final String NODE_MULTIMODAL = "multimodal";
    public static final String NODE_DECISION = "decision";
    public static final String NODE_APPROVAL = "approval";
    public static final String NODE_TOOLS = "tools";
    public static final String NODE_SUMMARY = "summary";

    private final LlmClient llmClient;
    private final ObjectMapper objectMapper;
    private final DecisionJsonParser decisionJsonParser;
    private final MultimodalSupport multimodalSupport;
    private final com.forge.modules.ai.agent.tools.TableSchemaInspector tableSchemaInspector;
    private final PromptBudgeter promptBudgeter;
    private final com.forge.modules.ai.client.NativeToolCalling nativeToolCalling;
    private final java.util.concurrent.Executor agentExecutor;
    private final com.forge.modules.ai.config.AiModuleConfig aiModuleConfig;

    public ConversationalAssistantGraph(LlmClient llmClient,
                                        ObjectMapper objectMapper,
                                        DecisionJsonParser decisionJsonParser,
                                        MultimodalSupport multimodalSupport,
                                        com.forge.modules.ai.agent.tools.TableSchemaInspector tableSchemaInspector,
                                        PromptBudgeter promptBudgeter,
                                        com.forge.modules.ai.client.NativeToolCalling nativeToolCalling,
                                        @org.springframework.beans.factory.annotation.Qualifier("agentExecutor")
                                        java.util.concurrent.Executor agentExecutor,
                                        com.forge.modules.ai.config.AiModuleConfig aiModuleConfig) {
        this.llmClient = llmClient;
        this.objectMapper = objectMapper;
        this.decisionJsonParser = decisionJsonParser;
        this.multimodalSupport = multimodalSupport;
        this.tableSchemaInspector = tableSchemaInspector;
        this.promptBudgeter = promptBudgeter;
        this.nativeToolCalling = nativeToolCalling;
        this.agentExecutor = agentExecutor;
        this.aiModuleConfig = aiModuleConfig;
    }

    @Override
    public String graphKey() {
        return "conversational-assistant";
    }

    @Override
    public StateGraph<AgentGraphState> build(AgentGraphContext ctx) throws org.bsc.langgraph4j.GraphStateException {
        return new StateGraph<>(AgentGraphState::new)
                .addNode(NODE_MULTIMODAL, multimodalSupport.node(ctx))
                .addNode(NODE_DECISION, decisionNode(ctx))
                .addNode(NODE_APPROVAL, org.bsc.langgraph4j.action.AsyncNodeActionWithConfig.node_async(approvalNode(ctx)))
                .addNode(NODE_TOOLS, toolsNode(ctx))
                .addNode(NODE_SUMMARY, summaryNode(ctx))
                .addEdge(StateGraph.START, NODE_MULTIMODAL)
                .addEdge(NODE_MULTIMODAL, NODE_DECISION)
                .addConditionalEdges(NODE_DECISION, decisionRouter(ctx), Map.of(
                        "tools", NODE_TOOLS,
                        "approval", NODE_APPROVAL,
                        "summary", NODE_SUMMARY))
                .addConditionalEdges(NODE_APPROVAL, AsyncEdgeAction.edge_async(state ->
                        "REJECTED".equals(state.value(AgentGraphState.APPROVAL).map(String::valueOf).orElse(""))
                                ? "rejected" : "approved"), Map.of(
                        "approved", NODE_TOOLS,
                        "rejected", NODE_SUMMARY))
                .addEdge(NODE_TOOLS, NODE_DECISION)
                .addEdge(NODE_SUMMARY, StateGraph.END);
    }

    /**
     * 确认节点：未确认时动态中断（GraphInterruptException），恢复后按审批结论路由
     */
    private org.bsc.langgraph4j.action.NodeActionWithConfig<AgentGraphState> approvalNode(AgentGraphContext ctx) {
        return (state, config) -> {
            if (state.value(AgentGraphState.APPROVAL).isEmpty()) {
                String detail = describePendingCalls(state);
                ctx.emit(AgentEvents.Type.STEP_END, NODE_APPROVAL, "人工确认", Map.of(
                        "seq", ctx.nextSeq(), "status", "INTERRUPTED",
                        "output", "高危工具调用，等待人工确认：" + detail));
                throw new org.bsc.langgraph4j.GraphInterruptException(config,
                        "Agent 判定需要执行高危工具，请人工确认：\n" + detail);
            }
            return Map.of();
        };
    }

    /**
     * 待执行的高危工具调用明细（写入待办内容展示，如具体 SQL）
     */
    @SuppressWarnings("unchecked")
    private String describePendingCalls(AgentGraphState state) {
        Object decisionObj = state.value(AgentGraphState.DECISION).orElse(null);
        if (!(decisionObj instanceof Map<?, ?> decision)) {
            return "（决策信息缺失）";
        }
        Object callsObj = decision.get("calls");
        if (!(callsObj instanceof List<?> calls) || calls.isEmpty()) {
            return "（无调用明细）";
        }
        StringBuilder sb = new StringBuilder();
        for (Object call : calls) {
            if (call instanceof Map<?, ?> c) {
                sb.append("工具: ").append(c.get("tool"));
                Object args = c.get("args");
                if (args != null) {
                    try {
                        sb.append("\n参数: ").append(objectMapper.writeValueAsString(args));
                    } catch (Exception ignored) {
                        sb.append("\n参数: ").append(args);
                    }
                }
                sb.append("\n\n");
            }
        }
        return sb.toString().trim();
    }

    // ========== 决策节点（LLM + 工具循环） ==========

    private AsyncNodeAction<AgentGraphState> decisionNode(AgentGraphContext ctx) {
        return AsyncNodeAction.node_async(state -> {
            AiModelConfig model = ctx.getNodeModels().get(NODE_DECISION);
            if (model == null) {
                throw new BusinessException("决策节点未绑定模型");
            }
            int seq = ctx.nextSeq();
            int steps = state.value(AgentGraphState.STEPS).map(v -> (Integer) v).orElse(0);
            ctx.emit(AgentEvents.Type.STEP_START, NODE_DECISION, "工具决策", Map.of(
                    "seq", seq, "model", model.getModelName(), "step", steps + 1));
            long start = System.currentTimeMillis();

            Map<String, Object> decision = decide(ctx, state, model, 0);
            ChatResponse last = (ChatResponse) decision.remove("__response");
            ctx.emit(AgentEvents.Type.STEP_END, NODE_DECISION, "工具决策", Map.of(
                    "seq", seq, "status", "SUCCESS",
                    "output", decisionJsonParser.truncate(String.valueOf(decision.get("__raw"))),
                    "tokensIn", last != null ? nullToZero(last.getInputTokens()) : 0,
                    "tokensOut", last != null ? nullToZero(last.getOutputTokens()) : 0,
                    "durationMs", System.currentTimeMillis() - start,
                    "modelId", model.getId()));
            decision.remove("__raw");
            return Map.of(
                    AgentGraphState.DECISION, decision,
                    AgentGraphState.STEPS, steps + 1);
        });
    }

    /**
     * 决策调用双轨：native tool-calling（结构化，无协议解析）优先，端点不支持或模式关闭时回退
     * JSON 协议（决策流式聚合 + decision-timeout + 并行 failover）；文本增量实时推送 THOUGHT 事件
     */
    private Map<String, Object> decide(AgentGraphContext ctx, AgentGraphState state,
                                       AiModelConfig model, int retry) {
        ChatResponse response;
        boolean nativeMode = false;
        java.util.function.Consumer<String> onDelta =
                delta -> ctx.emit(AgentEvents.Type.THOUGHT, NODE_DECISION, "工具决策", Map.of("delta", delta));
        if (!retryUsedNative() && nativeToolCalling.enabled(model)) {
            try {
                response = nativeToolCalling.call(buildNativeRequest(ctx, state), model,
                        ctx.getToolRegistry(), null, onDelta);
                nativeMode = true;
            } catch (NativeToolCalling.ToolsUnsupportedException e) {
                log.info("[agent-decision] native 不支持，回退 JSON 协议: {}", e.getMessage());
                response = null;
            } catch (Exception e) {
                log.warn("[agent-decision] native 调用失败，回退 JSON 协议: {}", e.getMessage());
                response = null;
            }
        } else {
            response = null;
        }
        if (response == null) {
            ChatRequest request = new ChatRequest();
            request.setContent(buildDecisionPrompt(ctx, state));
            request.setTemperature(0.1);
            response = llmClient.chatForDecision(request, model, onDelta);
        }
        if (!Boolean.TRUE.equals(response.getSuccess())) {
            throw new BusinessException("决策模型调用失败: " + response.getErrorMessage());
        }
        Map<String, Object> decision = decisionJsonParser.parseDecision(response.getContent());
        if (decision == null && !retryUsedNative()) {
            log.warn("[agent-decision] 解析失败（输出长度={}），retry={}",
                    response.getContent() == null ? 0 : response.getContent().length(), retry);
            if (retry < 1) {
                ChatRequest retryRequest = new ChatRequest();
                retryRequest.setContent(buildDecisionPrompt(ctx, state)
                        + "\n\n注意：上一次输出无法解析为合法 JSON（可能被截断或格式错误），请务必只输出一个完整合法的 JSON 对象。");
                retryRequest.setTemperature(0.1);
                ChatResponse retryResponse = llmClient.chatForDecision(retryRequest, model, onDelta);
                if (Boolean.TRUE.equals(retryResponse.getSuccess())) {
                    Map<String, Object> retryDecision = decisionJsonParser.parseDecision(retryResponse.getContent());
                    if (retryDecision != null) {
                        retryDecision.put("__response", retryResponse);
                        retryDecision.put("__raw", retryResponse.getContent());
                        return retryDecision;
                    }
                }
            }
            // 二次仍无法按协议解析：模型输出非空时按自然语言回答原文兜底；空内容判失败（避免假成功）
            if (response.getContent() == null || response.getContent().isBlank()) {
                throw new BusinessException("决策模型返回空内容（上游超时或限流），请重试");
            }
            decision = new LinkedHashMap<>();
            decision.put("action", "final");
            decision.put("answer", response.getContent());
        }
        decision.put("__response", response);
        decision.put("__raw", response.getContent());
        return decision;
    }

    /** native 轨提示词：不含 JSON 协议说明与工具清单文本（由 tools API 结构化传递），保留表清单/记忆/历史 */
    private ChatRequest buildNativeRequest(AgentGraphContext ctx, AgentGraphState state) {
        StringBuilder sb = new StringBuilder();
        sb.append(ctx.getAgentConfig().getSystemPrompt()).append('\n');
        String tables = promptBudgeter.renderTables(
                state.value(AgentGraphState.INPUT).map(String::valueOf).orElse(""),
                tableSchemaInspector.enabledTables());
        if (!tables.isBlank()) {
            sb.append("数据库可用表（编写 SQL 必须使用以下真实表名，禁止臆造）：\n").append(tables).append('\n');
        }
        String input = state.value(AgentGraphState.INPUT).map(String::valueOf).orElse("");
        String userTemplate = ctx.getAgentConfig().getUserPromptTemplate();
        sb.append(userTemplate == null || userTemplate.isBlank() ? input
                : userTemplate.replace("{input}", input)).append('\n');
        state.value(AgentGraphState.IMAGE_TEXT)
                .ifPresent(t -> sb.append("图片识别结果：").append(t).append('\n'));
        appendMemory(ctx, state, sb);
        String history = promptBudgeter.renderToolHistory(
                state.value(AgentGraphState.TOOL_HISTORY).map(v -> (List<?>) v).orElse(List.of()), null);
        if (!history.isBlank()) {
            sb.append("已执行的工具与结果：\n").append(history);
        }
        sb.append("\n请决定：需要工具则调用对应工具；已可回答则直接给出完整回答。");
        ChatRequest request = new ChatRequest();
        request.setContent(sb.toString());
        request.setTemperature(0.1);
        return request;
    }

    private boolean retryUsedNative() {
        // 重试路径固定走 JSON 协议（native 输出本就是结构化，无需重试提示）
        return false;
    }

    private void appendMemory(AgentGraphContext ctx, AgentGraphState state, StringBuilder sb) {
        String memory = state.value(AgentGraphState.CONVERSATION_MEMORY)
                .map(String::valueOf).orElse("");
        String rendered = promptBudgeter.renderMemory(memory);
        if (!rendered.isBlank()) {
            sb.append("先前对话结论（同会话历史，供参考，勿重复执行已完成的查询）：\n")
                    .append(rendered).append('\n');
        }
    }

    private String buildDecisionPrompt(AgentGraphContext ctx, AgentGraphState state) {
        StringBuilder sb = new StringBuilder();
        sb.append(ctx.getAgentConfig().getSystemPrompt()).append('\n');
        sb.append("可用工具：\n").append(ctx.getToolRegistry().describeTools()).append('\n');
        String tables = promptBudgeter.renderTables(
                state.value(AgentGraphState.INPUT).map(String::valueOf).orElse(""),
                tableSchemaInspector.enabledTables());
        if (!tables.isBlank()) {
            sb.append("数据库可用表（编写 SQL 必须使用以下真实表名，禁止臆造）：\n").append(tables).append('\n');
        }
        String input = state.value(AgentGraphState.INPUT).map(String::valueOf).orElse("");
        String userTemplate = ctx.getAgentConfig().getUserPromptTemplate();
        sb.append(userTemplate == null || userTemplate.isBlank() ? input
                : userTemplate.replace("{input}", input)).append('\n');
        state.value(AgentGraphState.IMAGE_TEXT)
                .ifPresent(t -> sb.append("图片识别结果：").append(t).append('\n'));
        appendMemory(ctx, state, sb);
        String history = promptBudgeter.renderToolHistory(
                state.value(AgentGraphState.TOOL_HISTORY).map(v -> (List<?>) v).orElse(List.of()), null);
        if (!history.isBlank()) {
            sb.append("已执行的工具与结果：\n").append(history);
        }
        sb.append("""

                请以严格 JSON 输出决策（不要包含其他文字、不要用 markdown 代码块包裹）：
                1. 需要调用工具：{"action":"tool","calls":[{"tool":"工具名","args":{参数}}],"thought":"原因"}
                2. 可以回答：{"action":"final","answer":"完整回答"}
                """);
        return sb.toString();
    }


    // ========== 条件路由 ==========

    private AsyncEdgeAction<AgentGraphState> decisionRouter(AgentGraphContext ctx) {
        return AsyncEdgeAction.edge_async(state -> {
            Map<?, ?> decision = state.value(AgentGraphState.DECISION)
                    .map(v -> (Map<?, ?>) v).orElse(Map.of());
            int steps = state.value(AgentGraphState.STEPS).map(v -> (Integer) v).orElse(0);
            int maxSteps = ctx.getAgentConfig().getMaxSteps() == null ? 8 : ctx.getAgentConfig().getMaxSteps();
            String action = String.valueOf(decision.get("action"));
            boolean approved = state.value(AgentGraphState.APPROVAL).isPresent();
            if ("tool".equals(action) && steps < maxSteps) {
                List<?> calls = (List<?>) decision.get("calls");
                List<String> toolNames = calls == null ? List.of() : calls.stream()
                        .map(c -> String.valueOf(((Map<?, ?>) c).get("tool"))).toList();
                if (!approved && ctx.getToolRegistry().hasHighRisk(toolNames)) {
                    return "approval";
                }
                return "tools";
            }
            if ("tool".equals(action)) {
                // 步数达上限强制收束:可见提示(不再静默)
                ctx.emit(AgentEvents.Type.STEP_END, NODE_DECISION, "工具决策", Map.of(
                        "seq", ctx.nextSeq(), "status", "SKIPPED",
                        "output", "已达最大步数 " + maxSteps + ",提前总结回答"));
            }
            return "summary";
        });
    }

    // ========== 工具执行节点（多调用并行） ==========

    private AsyncNodeAction<AgentGraphState> toolsNode(AgentGraphContext ctx) {
        return AsyncNodeAction.node_async(state -> {
            Map<?, ?> decision = state.value(AgentGraphState.DECISION)
                    .map(v -> (Map<?, ?>) v).orElse(Map.of());
            List<?> calls = (List<?>) decision.get("calls");
            List<Map<String, Object>> history = state.value(AgentGraphState.TOOL_HISTORY)
                    .map(v -> (List<Map<String, Object>>) v).orElse(new ArrayList<>());
            if (calls == null || calls.isEmpty()) {
                return Map.of();
            }
            long start = System.currentTimeMillis();
            List<Map<String, Object>> newHistory = new ArrayList<>();
            List<CompletableFuture<Void>> futures = new ArrayList<>();
            for (Object raw : calls) {
                Map<?, ?> call = (Map<?, ?>) raw;
                String toolName = String.valueOf(call.get("tool"));
                @SuppressWarnings("unchecked")
                Map<String, Object> args = call.get("args") == null
                        ? Map.of() : (Map<String, Object>) call.get("args");
                int callSeq = ctx.nextSeq();
                ctx.emit(AgentEvents.Type.TOOL_CALL, NODE_TOOLS, toolName, Map.of("seq", callSeq, "args", args));
                futures.add(CompletableFuture.runAsync(() -> {
                    long toolStart = System.currentTimeMillis();
                    Map<String, Object> record = new LinkedHashMap<>();
                    record.put("tool", toolName);
                    record.put("args", args);
                    try {
                        Object result = ctx.getToolRegistry().execute(toolName, args,
                                com.forge.modules.ai.agent.tools.ToolExecutionContext.of(ctx.getUserId()));
                        record.put("result", decisionJsonParser.truncate(String.valueOf(result)));
                        record.put("status", "SUCCESS");
                        ctx.emit(AgentEvents.Type.TOOL_RESULT, NODE_TOOLS, toolName, Map.of(
                                "seq", callSeq, "status", "SUCCESS", "output", decisionJsonParser.truncate(String.valueOf(result)),
                                "durationMs", System.currentTimeMillis() - toolStart));
                    } catch (Exception e) {
                        record.put("error", e.getMessage());
                        record.put("status", "FAILED");
                        ctx.emit(AgentEvents.Type.TOOL_RESULT, NODE_TOOLS, toolName, Map.of(
                                "seq", callSeq, "status", "FAILED", "error", e.getMessage(),
                                "durationMs", System.currentTimeMillis() - toolStart));
                    }
                    synchronized (newHistory) {
                        newHistory.add(record);
                    }
                }, agentExecutor));
            }
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            history.addAll(newHistory);
            ctx.emit(AgentEvents.Type.STEP_END, NODE_TOOLS, "工具执行", Map.of(
                    "seq", ctx.nextSeq(), "status", "SUCCESS",
                    "calls", newHistory.size(), "durationMs", System.currentTimeMillis() - start));
            return Map.of(AgentGraphState.TOOL_HISTORY, history);
        });
    }

    // ========== 总结节点 ==========

    private AsyncNodeAction<AgentGraphState> summaryNode(AgentGraphContext ctx) {
        return AsyncNodeAction.node_async(state -> {
            if ("REJECTED".equals(state.value(AgentGraphState.APPROVAL).map(String::valueOf).orElse(""))) {
                String answer = "该请求包含高危操作，已被人工审核驳回，未执行。";
                ctx.emit(AgentEvents.Type.STEP_END, NODE_SUMMARY, "总结回答", Map.of(
                        "seq", ctx.nextSeq(), "status", "SUCCESS", "output", answer));
                return Map.of(AgentGraphState.FINAL_ANSWER, answer);
            }
            Map<?, ?> decision = state.value(AgentGraphState.DECISION)
                    .map(v -> (Map<?, ?>) v).orElse(Map.of());
            if ("final".equals(String.valueOf(decision.get("action")))) {
                String answer = decision.get("answer") == null ? "" : String.valueOf(decision.get("answer"));
                ctx.emit(AgentEvents.Type.STEP_END, NODE_SUMMARY, "总结回答", Map.of(
                        "seq", ctx.nextSeq(), "status", "SUCCESS", "output", decisionJsonParser.truncate(answer)));
                return Map.of(AgentGraphState.FINAL_ANSWER, answer);
            }
            AiModelConfig model = ctx.getNodeModels().get(NODE_SUMMARY);
            if (model == null) {
                model = ctx.getNodeModels().get(NODE_DECISION);
            }
            if (model == null) {
                throw new BusinessException("总结节点未绑定模型");
            }
            int seq = ctx.nextSeq();
            long start = System.currentTimeMillis();
            ctx.emit(AgentEvents.Type.STEP_START, NODE_SUMMARY, "总结回答", Map.of(
                    "seq", seq, "model", model.getModelName()));
            StringBuilder sb = new StringBuilder();
            sb.append("用户问题：").append(state.value(AgentGraphState.INPUT).map(String::valueOf).orElse("")).append('\n');
            state.value(AgentGraphState.IMAGE_TEXT)
                    .ifPresent(t -> sb.append("图片识别结果：").append(t).append('\n'));
            List<?> history = state.value(AgentGraphState.TOOL_HISTORY)
                    .map(v -> (List<?>) v).orElse(List.of());
            sb.append("工具执行记录：\n");
            for (Object h : history) {
                sb.append("- ").append(h).append('\n');
            }
            sb.append("请基于以上信息给出完整、准确的最终回答。");

            ChatRequest request = new ChatRequest();
            request.setContent(sb.toString());
            request.setTemperature(0.5);
            // 流式总结：token 级 CONTENT 增量事件，前端打字机渲染
            StringBuilder answerBuf = new StringBuilder();
            llmClient.stream(request, model)
                    .doOnNext(frame -> {
                        String delta = extractContentDelta(frame);
                        if (delta != null && !delta.isEmpty()) {
                            answerBuf.append(delta);
                            ctx.emit(AgentEvents.Type.CONTENT, NODE_SUMMARY, "总结回答", Map.of("delta", delta));
                        }
                    })
                    .blockLast(java.time.Duration.ofMillis(summaryTimeoutMs()));
            String answer = answerBuf.toString();
            if (answer.isBlank()) {
                ChatResponse response = llmClient.chat(request, model);
                if (!Boolean.TRUE.equals(response.getSuccess())) {
                    throw new BusinessException("总结模型调用失败: " + response.getErrorMessage());
                }
                answer = response.getContent() == null ? "" : response.getContent();
                ctx.emit(AgentEvents.Type.CONTENT, NODE_SUMMARY, "总结回答", Map.of("delta", answer));
            }
            ctx.emit(AgentEvents.Type.STEP_END, NODE_SUMMARY, "总结回答", Map.of(
                    "seq", seq, "status", "SUCCESS", "output", decisionJsonParser.truncate(answer),
                    "durationMs", System.currentTimeMillis() - start,
                    "modelId", model.getId()));
            return Map.of(AgentGraphState.FINAL_ANSWER, answer);
        });
    }

    /**
     * 从 LlmClient 的 SSE 帧字符串（{"content":"…"} / [DONE] / {"error":…}）提取文本增量
     */
    private String extractContentDelta(String frame) {
        if (frame == null || "[DONE]".equals(frame.trim())) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(frame);
            if (node.has("error") && node.path("error").asBoolean(false)) {
                throw new BusinessException("流式总结失败: " + node.path("message").asText());
            }
            return node.path("content").asText("");
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            return null;
        }
    }

    private long summaryTimeoutMs() {
        Integer timeout = aiModuleConfig.getClient().getSummaryTimeout();
        return timeout == null || timeout <= 0 ? 60000L : timeout;
    }

    private int nullToZero(Integer value) {
        return value == null ? 0 : value;
    }
}
