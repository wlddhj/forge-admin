package com.forge.modules.ai.agent.graph;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.forge.common.exception.BusinessException;
import com.forge.modules.ai.agent.AgentEvents;
import com.forge.modules.ai.agent.kernel.AgentGraphContext;
import com.forge.modules.ai.agent.kernel.AgentGraphDefinition;
import com.forge.modules.ai.agent.kernel.AgentGraphState;
import com.forge.modules.ai.client.LlmClient;
import com.forge.modules.ai.dto.request.ChatRequest;
import com.forge.modules.ai.dto.response.ChatResponse;
import com.forge.modules.ai.entity.AiModelConfig;
import lombok.RequiredArgsConstructor;
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
@RequiredArgsConstructor
public class ConversationalAssistantGraph implements AgentGraphDefinition {

    public static final String NODE_MULTIMODAL = "multimodal";
    public static final String NODE_DECISION = "decision";
    public static final String NODE_APPROVAL = "approval";
    public static final String NODE_TOOLS = "tools";
    public static final String NODE_SUMMARY = "summary";

    private final LlmClient llmClient;
    private final ObjectMapper objectMapper;

    @Override
    public String graphKey() {
        return "conversational-assistant";
    }

    @Override
    public StateGraph<AgentGraphState> build(AgentGraphContext ctx) throws org.bsc.langgraph4j.GraphStateException {
        return new StateGraph<>(AgentGraphState::new)
                .addNode(NODE_MULTIMODAL, multimodalNode(ctx))
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
                ctx.emit(AgentEvents.Type.STEP_END, NODE_APPROVAL, "人工确认", Map.of(
                        "seq", ctx.nextSeq(), "status", "INTERRUPTED",
                        "output", "高危工具调用，等待人工确认"));
                throw new org.bsc.langgraph4j.GraphInterruptException(config, "高危工具调用待人工确认");
            }
            return Map.of();
        };
    }

    // ========== 图片理解节点 ==========

    private AsyncNodeAction<AgentGraphState> multimodalNode(AgentGraphContext ctx) {
        return AsyncNodeAction.node_async(state -> {
            List<String> images = state.value(AgentGraphState.IMAGES)
                    .map(v -> (List<String>) v).orElse(List.of());
            if (images.isEmpty()) {
                return Map.of();
            }
            AiModelConfig model = ctx.getNodeModels().get(NODE_MULTIMODAL);
            int seq = ctx.nextSeq();
            if (model == null) {
                ctx.emit(AgentEvents.Type.STEP_END, NODE_MULTIMODAL, "图片理解", Map.of(
                        "seq", seq, "status", "SKIPPED", "output", "未配置多模态模型(model_type=multimodal)，已跳过图片理解"));
                return Map.of(AgentGraphState.IMAGE_TEXT, "（未配置视觉模型，无法识别图片）");
            }
            long start = System.currentTimeMillis();
            ctx.emit(AgentEvents.Type.STEP_START, NODE_MULTIMODAL, "图片理解", Map.of(
                    "seq", seq, "model", model.getModelName(), "images", images.size()));
            ChatRequest request = new ChatRequest();
            request.setContent("请识别并详细描述图片内容；若图片包含文字，请完整提取（OCR）。");
            request.setImages(images);
            request.setTemperature(0.2);
            ChatResponse response = llmClient.chat(request, model);
            if (!Boolean.TRUE.equals(response.getSuccess())) {
                throw new BusinessException("图片理解失败: " + response.getErrorMessage());
            }
            String imageText = response.getContent() == null ? "" : response.getContent();
            ctx.emit(AgentEvents.Type.STEP_END, NODE_MULTIMODAL, "图片理解", Map.of(
                    "seq", seq, "status", "SUCCESS",
                    "output", truncate(imageText),
                    "tokensIn", nullToZero(response.getInputTokens()),
                    "tokensOut", nullToZero(response.getOutputTokens()),
                    "durationMs", System.currentTimeMillis() - start,
                    "modelId", model.getId()));
            return Map.of(AgentGraphState.IMAGE_TEXT, imageText);
        });
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
                    "output", truncate(String.valueOf(decision.get("__raw"))),
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

    private Map<String, Object> decide(AgentGraphContext ctx, AgentGraphState state,
                                       AiModelConfig model, int retry) {
        String prompt = buildDecisionPrompt(ctx, state);
        ChatRequest request = new ChatRequest();
        request.setContent(prompt);
        request.setTemperature(0.1);
        ChatResponse response = llmClient.chat(request, model);
        if (!Boolean.TRUE.equals(response.getSuccess())) {
            throw new BusinessException("决策模型调用失败: " + response.getErrorMessage());
        }
        Map<String, Object> decision = parseDecision(response.getContent());
        if (decision == null) {
            if (retry < 1) {
                return decide(ctx, state, model, retry + 1);
            }
            decision = new LinkedHashMap<>();
            decision.put("action", "final");
            decision.put("answer", "工具调用格式异常，基于现有信息直接回答。");
        }
        decision.put("__response", response);
        decision.put("__raw", response.getContent());
        return decision;
    }

    private String buildDecisionPrompt(AgentGraphContext ctx, AgentGraphState state) {
        StringBuilder sb = new StringBuilder();
        sb.append(ctx.getAgentConfig().getSystemPrompt()).append('\n');
        sb.append("可用工具：\n").append(ctx.getToolRegistry().describeTools()).append('\n');
        String input = state.value(AgentGraphState.INPUT).map(String::valueOf).orElse("");
        String userTemplate = ctx.getAgentConfig().getUserPromptTemplate();
        sb.append(userTemplate == null || userTemplate.isBlank() ? input
                : userTemplate.replace("{input}", input)).append('\n');
        state.value(AgentGraphState.IMAGE_TEXT)
                .ifPresent(t -> sb.append("图片识别结果：").append(t).append('\n'));
        List<?> history = state.value(AgentGraphState.TOOL_HISTORY)
                .map(v -> (List<?>) v).orElse(List.of());
        if (!history.isEmpty()) {
            sb.append("已执行的工具与结果：\n");
            for (Object h : history) {
                sb.append("- ").append(h).append('\n');
            }
        }
        sb.append("""

                请以严格 JSON 输出决策（不要包含其他文字、不要用 markdown 代码块包裹）：
                1. 需要调用工具：{"action":"tool","calls":[{"tool":"工具名","args":{参数}}],"thought":"原因"}
                2. 可以回答：{"action":"final","answer":"完整回答"}
                """);
        return sb.toString();
    }

    private Map<String, Object> parseDecision(String content) {
        if (content == null) {
            return null;
        }
        String json = content.trim();
        int start = json.indexOf('{');
        int end = json.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(json.substring(start, end + 1));
            String action = node.path("action").asText("");
            Map<String, Object> decision = new LinkedHashMap<>();
            if ("tool".equals(action) && node.path("calls").isArray() && node.path("calls").size() > 0) {
                List<Map<String, Object>> calls = new ArrayList<>();
                for (JsonNode call : node.path("calls")) {
                    Map<String, Object> c = new LinkedHashMap<>();
                    c.put("tool", call.path("tool").asText());
                    c.put("args", objectMapper.convertValue(call.path("args"), Map.class));
                    calls.add(c);
                }
                decision.put("action", "tool");
                decision.put("calls", calls);
                decision.put("thought", node.path("thought").asText(""));
            } else if ("final".equals(action)) {
                decision.put("action", "final");
                decision.put("answer", node.path("answer").asText(""));
            } else {
                return null;
            }
            return decision;
        } catch (Exception e) {
            return null;
        }
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
                        Object result = ctx.getToolRegistry().get(toolName).execute(args);
                        record.put("result", truncate(String.valueOf(result)));
                        record.put("status", "SUCCESS");
                        ctx.emit(AgentEvents.Type.TOOL_RESULT, NODE_TOOLS, toolName, Map.of(
                                "seq", callSeq, "status", "SUCCESS", "output", truncate(String.valueOf(result)),
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
                }));
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
                        "seq", ctx.nextSeq(), "status", "SUCCESS", "output", truncate(answer)));
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
            ChatResponse response = llmClient.chat(request, model);
            if (!Boolean.TRUE.equals(response.getSuccess())) {
                throw new BusinessException("总结模型调用失败: " + response.getErrorMessage());
            }
            String answer = response.getContent() == null ? "" : response.getContent();
            ctx.emit(AgentEvents.Type.STEP_END, NODE_SUMMARY, "总结回答", Map.of(
                    "seq", seq, "status", "SUCCESS", "output", truncate(answer),
                    "tokensIn", nullToZero(response.getInputTokens()),
                    "tokensOut", nullToZero(response.getOutputTokens()),
                    "durationMs", System.currentTimeMillis() - start,
                    "modelId", model.getId()));
            return Map.of(AgentGraphState.FINAL_ANSWER, answer);
        });
    }

    private String truncate(String text) {
        if (text == null) {
            return "";
        }
        return text.length() > 3000 ? text.substring(0, 3000) + "…(截断)" : text;
    }

    private int nullToZero(Integer value) {
        return value == null ? 0 : value;
    }
}
