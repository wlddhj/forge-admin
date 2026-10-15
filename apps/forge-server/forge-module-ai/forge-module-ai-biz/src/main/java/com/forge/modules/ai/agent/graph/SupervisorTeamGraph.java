package com.forge.modules.ai.agent.graph;

import com.fasterxml.jackson.core.type.TypeReference;
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
import com.forge.modules.ai.entity.AiAgentConfig;
import com.forge.modules.ai.entity.AiAgentTeamMember;
import com.forge.modules.ai.entity.AiModelConfig;
import com.forge.modules.ai.mapper.AiAgentConfigMapper;
import com.forge.modules.ai.mapper.AiModelConfigMapper;
import lombok.RequiredArgsConstructor;
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
 * Supervisor 调度式多 Agent 协作图：
 * supervisor（路由分派）→ expert_decide（专家决策）→ [expert_approval] → expert_tools → 循环
 * → expert_report（汇报回填）→ supervisor … → summary（流式总结）
 * 专家为逻辑子图：独立 prompt/模型/工具白名单/循环上限，复用单 Agent 的审批中断与检查点机制
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class SupervisorTeamGraph implements AgentGraphDefinition {

    public static final String GRAPH_KEY = "team-supervisor";
    public static final String NODE_SUPERVISOR = "supervisor";
    public static final String NODE_EXPERT_DECIDE = "expert_decide";
    public static final String NODE_EXPERT_APPROVAL = "expert_approval";
    public static final String NODE_EXPERT_TOOLS = "expert_tools";
    public static final String NODE_EXPERT_REPORT = "expert_report";
    public static final String NODE_SUMMARY = "summary";

    private static final String KEY_SUPERVISOR = "supervisor";
    private static final String KEY_SUMMARY = "summary";

    private final LlmClient llmClient;
    private final ObjectMapper objectMapper;
    private final DecisionJsonParser decisionJsonParser;
    private final com.forge.modules.ai.agent.tools.TableSchemaInspector tableSchemaInspector;
    private final AiAgentConfigMapper agentConfigMapper;
    private final AiModelConfigMapper modelConfigMapper;

    @Override
    public String graphKey() {
        return GRAPH_KEY;
    }

    @Override
    public StateGraph<AgentGraphState> build(AgentGraphContext ctx) throws org.bsc.langgraph4j.GraphStateException {
        if (ctx.getTeam() == null || ctx.getTeamMembers() == null) {
            throw new BusinessException("team-supervisor 图需要团队上下文");
        }
        return new StateGraph<>(AgentGraphState::new)
                .addNode(NODE_SUPERVISOR, supervisorNode(ctx))
                .addNode(NODE_EXPERT_DECIDE, expertDecideNode(ctx))
                .addNode(NODE_EXPERT_APPROVAL, org.bsc.langgraph4j.action.AsyncNodeActionWithConfig.node_async(expertApprovalNode(ctx)))
                .addNode(NODE_EXPERT_TOOLS, expertToolsNode(ctx))
                .addNode(NODE_EXPERT_REPORT, expertReportNode(ctx))
                .addNode(NODE_SUMMARY, summaryNode(ctx))
                .addEdge(StateGraph.START, NODE_SUPERVISOR)
                .addConditionalEdges(NODE_SUPERVISOR, AsyncEdgeAction.edge_async(state -> {
                    Map<?, ?> decision = state.value(AgentGraphState.DECISION)
                            .map(v -> (Map<?, ?>) v).orElse(Map.of());
                    int rounds = state.value(AgentGraphState.STEPS).map(v -> (Integer) v).orElse(0);
                    int maxRounds = ctx.getAgentConfig().getMaxSteps() == null ? 6 : ctx.getAgentConfig().getMaxSteps();
                    return "dispatch".equals(decision.get("action")) && rounds < maxRounds
                            ? "dispatch" : "summary";
                }), Map.of("dispatch", NODE_EXPERT_DECIDE, "summary", NODE_SUMMARY))
                .addConditionalEdges(NODE_EXPERT_DECIDE, AsyncEdgeAction.edge_async(state -> {
                    Map<?, ?> decision = state.value(AgentGraphState.DECISION)
                            .map(v -> (Map<?, ?>) v).orElse(Map.of());
                    Map<?, ?> dispatch = state.value(AgentGraphState.CURRENT_DISPATCH)
                            .map(v -> (Map<?, ?>) v).orElse(Map.of());
                    int expertSteps = state.value(AgentGraphState.EXPERT_STEPS).map(v -> (Integer) v).orElse(0);
                    int limit = dispatch.get("maxSteps") instanceof Number n ? n.intValue() : 8;
                    boolean approved = "APPROVED".equals(state.value(AgentGraphState.EXPERT_APPROVAL)
                            .map(String::valueOf).orElse(""));
                    if (!"tool".equals(decision.get("action")) || expertSteps > limit
                            || !(decision.get("calls") instanceof List<?> calls) || calls.isEmpty()) {
                        return "done";
                    }
                    List<String> toolNames = calls.stream()
                            .map(c -> String.valueOf(((Map<?, ?>) c).get("tool"))).toList();
                    return !approved && ctx.getToolRegistry().hasHighRisk(toolNames) ? "approval" : "tools";
                }), Map.of("tools", NODE_EXPERT_TOOLS, "approval", NODE_EXPERT_APPROVAL, "done", NODE_EXPERT_REPORT))
                .addConditionalEdges(NODE_EXPERT_APPROVAL, AsyncEdgeAction.edge_async(state ->
                        "REJECTED".equals(state.value(AgentGraphState.EXPERT_APPROVAL)
                                .map(String::valueOf).orElse(""))
                                ? "rejected" : "approved"), Map.of(
                        "approved", NODE_EXPERT_TOOLS,
                        "rejected", NODE_EXPERT_REPORT))
                .addEdge(NODE_EXPERT_TOOLS, NODE_EXPERT_DECIDE)
                .addEdge(NODE_EXPERT_REPORT, NODE_SUPERVISOR)
                .addEdge(NODE_SUMMARY, StateGraph.END);
    }

    // ========== Supervisor 调度节点 ==========

    /** 成员条目：成员配置 + 引用的 Agent 配置（Agent 不存在/未启用时为 null） */
    private record MemberEntry(AiAgentTeamMember member, AiAgentConfig agent) {
    }

    /** 一次批量查询构建 agentCode → 成员条目索引（整轮复用，避免循环内查库） */
    private Map<String, MemberEntry> buildMemberIndex(AgentGraphContext ctx) {
        List<Long> agentIds = ctx.getTeamMembers().stream()
                .map(AiAgentTeamMember::getAgentId).distinct().toList();
        Map<Long, AiAgentConfig> agents = agentIds.isEmpty() ? Map.of()
                : agentConfigMapper.selectBatchIds(agentIds).stream()
                        .collect(java.util.stream.Collectors.toMap(AiAgentConfig::getId, java.util.function.Function.identity()));
        Map<String, MemberEntry> index = new LinkedHashMap<>();
        for (AiAgentTeamMember m : ctx.getTeamMembers()) {
            AiAgentConfig agent = agents.get(m.getAgentId());
            if (agent != null && agent.getStatus() != null && agent.getStatus() == 1) {
                index.put(agent.getAgentCode(), new MemberEntry(m, agent));
            }
        }
        return index;
    }

    private AsyncNodeAction<AgentGraphState> supervisorNode(AgentGraphContext ctx) {
        return AsyncNodeAction.node_async(state -> {
            AiModelConfig model = ctx.getNodeModels().get(KEY_SUPERVISOR);
            if (model == null) {
                throw new BusinessException("Supervisor 节点未绑定模型（团队 model_bindings.supervisor）");
            }
            int seq = ctx.nextSeq();
            int rounds = state.value(AgentGraphState.STEPS).map(v -> (Integer) v).orElse(0);
            ctx.emit(AgentEvents.Type.STEP_START, NODE_SUPERVISOR, "调度决策", Map.of(
                    "seq", seq, "model", model.getModelName(), "round", rounds + 1));
            long start = System.currentTimeMillis();

            Map<String, MemberEntry> index = buildMemberIndex(ctx);
            if (index.isEmpty()) {
                throw new BusinessException("团队没有可用成员（成员引用的 Agent 均不存在或未启用）");
            }
            Map<String, Object> decision = decideSupervisor(ctx, state, model, index, 0);
            ChatResponse last = (ChatResponse) decision.remove("__response");
            ctx.emit(AgentEvents.Type.STEP_END, NODE_SUPERVISOR, "调度决策", Map.of(
                    "seq", seq, "status", "SUCCESS",
                    "output", decisionJsonParser.truncate(String.valueOf(decision.get("__raw"))),
                    "tokensIn", last != null ? nullToZero(last.getInputTokens()) : 0,
                    "tokensOut", last != null ? nullToZero(last.getOutputTokens()) : 0,
                    "durationMs", System.currentTimeMillis() - start,
                    "modelId", model.getId()));
            decision.remove("__raw");

            if ("final".equals(decision.get("action"))) {
                return Map.of(
                        AgentGraphState.DECISION, decision,
                        AgentGraphState.STEPS, rounds + 1,
                        AgentGraphState.SUPERVISOR_ANSWER, String.valueOf(decision.get("answer")));
            }
            MemberEntry entry = index.get(String.valueOf(decision.get("agent")));
            Map<String, Object> dispatch = new LinkedHashMap<>();
            dispatch.put("agentId", entry.member().getAgentId());
            dispatch.put("agentCode", entry.agent().getAgentCode());
            dispatch.put("agentName", entry.agent().getAgentName());
            dispatch.put("task", String.valueOf(decision.get("task")));
            dispatch.put("maxSteps", entry.member().getMaxSteps() == null ? 8 : entry.member().getMaxSteps());
            dispatch.put("allowedTools", parseAllowedTools(entry.member()));
            ctx.emit(AgentEvents.Type.STEP_END, NODE_SUPERVISOR, "任务分派", Map.of(
                    "seq", ctx.nextSeq(), "status", "SUCCESS",
                    "output", "分派给 [" + dispatch.get("agentName") + "]：" + dispatch.get("task"),
                    "agentCode", dispatch.get("agentCode"), "agentName", dispatch.get("agentName")));
            return Map.of(
                    AgentGraphState.DECISION, decision,
                    AgentGraphState.STEPS, rounds + 1,
                    AgentGraphState.CURRENT_DISPATCH, dispatch,
                    AgentGraphState.EXPERT_STEPS, 0);
        });
    }

    private Map<String, Object> decideSupervisor(AgentGraphContext ctx, AgentGraphState state,
                                                 AiModelConfig model, Map<String, MemberEntry> index, int retry) {
        String prompt = buildSupervisorPrompt(ctx, state, index);
        ChatRequest request = new ChatRequest();
        request.setContent(prompt);
        request.setTemperature(0.1);
        ChatResponse response = llmClient.chat(request, model);
        if (!Boolean.TRUE.equals(response.getSuccess())) {
            throw new BusinessException("Supervisor 模型调用失败: " + response.getErrorMessage());
        }
        Map<String, Object> decision = parseSupervisorDecision(index, response.getContent());
        if (decision == null) {
            log.warn("[team-supervisor] 决策解析失败 retry={} 输出长度={}", retry,
                    response.getContent() == null ? 0 : response.getContent().length());
            if (retry < 1) {
                ChatRequest retryRequest = new ChatRequest();
                retryRequest.setContent(prompt
                        + "\n\n注意：上一次输出无法解析为合法 JSON，请务必只输出一个完整合法的 JSON 对象。");
                retryRequest.setTemperature(0.1);
                ChatResponse retryResponse = llmClient.chat(retryRequest, model);
                if (Boolean.TRUE.equals(retryResponse.getSuccess())) {
                    Map<String, Object> retryDecision = parseSupervisorDecision(index, retryResponse.getContent());
                    if (retryDecision != null) {
                        retryDecision.put("__response", retryResponse);
                        retryDecision.put("__raw", retryResponse.getContent());
                        return retryDecision;
                    }
                }
            }
            if (response.getContent() == null || response.getContent().isBlank()) {
                throw new BusinessException("Supervisor 模型返回空内容（上游超时或限流），请重试");
            }
            decision = new LinkedHashMap<>();
            decision.put("action", "final");
            decision.put("answer", response.getContent());
        }
        decision.put("__response", response);
        decision.put("__raw", response.getContent());
        return decision;
    }

    private String buildSupervisorPrompt(AgentGraphContext ctx, AgentGraphState state,
                                         Map<String, MemberEntry> index) {
        StringBuilder sb = new StringBuilder();
        sb.append(ctx.getAgentConfig().getSystemPrompt()).append('\n');
        sb.append("团队专家清单：\n");
        for (MemberEntry entry : index.values()) {
            sb.append("- code: ").append(entry.agent().getAgentCode())
                    .append(" | 名称: ").append(entry.agent().getAgentName())
                    .append(" | 职责: ").append(entry.member().getMemberRole());
            List<String> allowed = parseAllowedTools(entry.member());
            if (!allowed.isEmpty()) {
                sb.append(" | 可用工具: ").append(String.join(",", allowed));
            }
            sb.append('\n');
        }
        sb.append("用户任务：").append(state.value(AgentGraphState.INPUT)
                .map(String::valueOf).orElse("")).append('\n');
        List<?> reports = state.value(AgentGraphState.AGENT_REPORTS)
                .map(v -> (List<?>) v).orElse(List.of());
        if (!reports.isEmpty()) {
            sb.append("已完成的专家汇报：\n");
            for (Object r : reports) {
                sb.append("- ").append(r).append('\n');
            }
        }
        sb.append("""

                请以严格 JSON 输出决策（不要包含其他文字、不要用 markdown 代码块包裹）：
                1. 需要分派专家执行：{"action":"dispatch","agent":"专家code","task":"明确的子任务指令","thought":"原因"}
                2. 任务已完成可回答：{"action":"final","answer":"综合所有专家汇报的完整回答"}
                注意：agent 必须取自上述团队专家清单中的 code；信息不足以回答时优先分派专家。
                """);
        return sb.toString();
    }

    /**
     * 解析 Supervisor 决策：dispatch 的 agent 必须在成员清单内，否则视为解析失败
     */
    private Map<String, Object> parseSupervisorDecision(Map<String, MemberEntry> index, String content) {
        if (content == null) {
            return null;
        }
        for (JsonNode node : decisionJsonParser.extractJsonObjects(content.trim())) {
            String action = node.path("action").asText("");
            if ("dispatch".equals(action)) {
                String agent = node.path("agent").asText("");
                if (!agent.isBlank() && index.containsKey(agent) && !node.path("task").asText("").isBlank()) {
                    Map<String, Object> decision = new LinkedHashMap<>();
                    decision.put("action", "dispatch");
                    decision.put("agent", agent);
                    decision.put("task", node.path("task").asText());
                    decision.put("thought", node.path("thought").asText(""));
                    return decision;
                }
                return null;
            }
            if ("final".equals(action) && !node.path("answer").asText("").isBlank()) {
                Map<String, Object> decision = new LinkedHashMap<>();
                decision.put("action", "final");
                decision.put("answer", node.path("answer").asText());
                return decision;
            }
        }
        return null;
    }

    private List<String> parseAllowedTools(AiAgentTeamMember member) {
        if (member.getAllowedTools() == null || member.getAllowedTools().isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(member.getAllowedTools(), new TypeReference<List<String>>() {
            });
        } catch (Exception e) {
            return List.of();
        }
    }

    // ========== 专家决策节点 ==========

    private AsyncNodeAction<AgentGraphState> expertDecideNode(AgentGraphContext ctx) {
        return AsyncNodeAction.node_async(state -> {
            Map<?, ?> dispatch = state.value(AgentGraphState.CURRENT_DISPATCH)
                    .map(v -> (Map<?, ?>) v).orElse(Map.of());
            String agentCode = str(dispatch, "agentCode");
            if (agentCode.isBlank() || "null".equals(agentCode)) {
                return Map.of(AgentGraphState.DECISION,
                        Map.of("action", "final", "answer", "（无有效分派）"));
            }
            String agentName = str(dispatch, "agentName", agentCode);
            @SuppressWarnings("unchecked")
            List<String> allowedTools = dispatch.get("allowedTools") instanceof List<?> l
                    ? (List<String>) l : List.of();
            int limit = dispatch.get("maxSteps") instanceof Number n ? n.intValue() : 8;
            int expertSteps = state.value(AgentGraphState.EXPERT_STEPS).map(v -> (Integer) v).orElse(0);

            // 降级路径：专家不可用或达步数上限，不调用模型直接产出汇报性结论
            AiAgentConfig agent = agentConfigMapper.selectById(
                    dispatch.get("agentId") instanceof Number n ? n.longValue() : -1L);
            String degraded = degradedReason(ctx, state, agent, dispatch, limit, expertSteps);
            if (degraded != null) {
                ctx.emit(AgentEvents.Type.STEP_END, NODE_EXPERT_DECIDE, "[" + agentName + "] 决策", Map.of(
                        "seq", ctx.nextSeq(), "status", "SKIPPED", "output", degraded,
                        "agentCode", agentCode, "agentName", agentName));
                return Map.of(
                        AgentGraphState.DECISION, finalDecision(degraded),
                        AgentGraphState.EXPERT_STEPS, expertSteps + 1);
            }

            Map<String, Object> decision = decideExpert(ctx, state, agent, dispatch, allowedTools, expertSteps, 0);
            ChatResponse last = (ChatResponse) decision.remove("__response");
            String raw = String.valueOf(decision.remove("__raw"));
            ctx.emit(AgentEvents.Type.STEP_END, NODE_EXPERT_DECIDE, "[" + agentName + "] 决策", Map.of(
                    "seq", ((Number) decision.remove("__seq")).intValue(), "status", "SUCCESS",
                    "output", decisionJsonParser.truncate(raw),
                    "tokensIn", last != null ? nullToZero(last.getInputTokens()) : 0,
                    "tokensOut", last != null ? nullToZero(last.getOutputTokens()) : 0,
                    "durationMs", decision.remove("__durationMs"),
                    "modelId", decision.remove("__modelId"),
                    "agentCode", agentCode, "agentName", agentName));
            return Map.of(
                    AgentGraphState.DECISION, decision,
                    AgentGraphState.EXPERT_STEPS, expertSteps + 1);
        });
    }

    /**
     * 降级原因（专家不可用 / 决策模型未绑定 / 达步数上限），可执行返回 null
     */
    private String degradedReason(AgentGraphContext ctx, AgentGraphState state, AiAgentConfig agent,
                                  Map<?, ?> dispatch, int limit, int expertSteps) {
        if (agent == null || agent.getStatus() == null || agent.getStatus() != 1) {
            return "（专家不可用：配置缺失或未启用）";
        }
        if (resolveDecisionModel(agent) == null) {
            return "（专家不可用：决策模型未绑定，需在其 Agent 配置 model_bindings.decision）";
        }
        if (expertSteps > limit) {
            return "（达到该专家步数上限，提前结束。近期执行记录：\n"
                    + recentToolHistory(ctx, state, str(dispatch, "agentCode")) + "）";
        }
        return null;
    }

    private Map<String, Object> decideExpert(AgentGraphContext ctx, AgentGraphState state, AiAgentConfig agent,
                                             Map<?, ?> dispatch, List<String> allowedTools, int expertSteps, int retry) {
        String agentCode = str(dispatch, "agentCode");
        String agentName = str(dispatch, "agentName", agentCode);
        String task = str(dispatch, "task");
        AiModelConfig model = resolveDecisionModel(agent);
        int seq = ctx.nextSeq();
        ctx.emit(AgentEvents.Type.STEP_START, NODE_EXPERT_DECIDE, "[" + agentName + "] 决策", Map.of(
                "seq", seq, "model", model.getModelName(), "step", expertSteps + 1,
                "agentCode", agentCode, "agentName", agentName));
        long start = System.currentTimeMillis();

        String prompt = buildExpertPrompt(ctx, state, agent, task, allowedTools, agentCode);
        ChatRequest request = new ChatRequest();
        request.setContent(prompt);
        request.setTemperature(0.1);
        ChatResponse response = llmClient.chat(request, model);
        if (!Boolean.TRUE.equals(response.getSuccess())) {
            throw new BusinessException("专家决策模型调用失败 [" + agentName + "]: " + response.getErrorMessage());
        }
        Map<String, Object> decision = decisionJsonParser.parseDecision(response.getContent());
        if (decision == null) {
            if (retry < 1) {
                ChatRequest retryRequest = new ChatRequest();
                retryRequest.setContent(prompt
                        + "\n\n注意：上一次输出无法解析为合法 JSON，请务必只输出一个完整合法的 JSON 对象。");
                retryRequest.setTemperature(0.1);
                ChatResponse retryResponse = llmClient.chat(retryRequest, model);
                if (Boolean.TRUE.equals(retryResponse.getSuccess())) {
                    Map<String, Object> retryDecision = decisionJsonParser.parseDecision(retryResponse.getContent());
                    if (retryDecision != null) {
                        return decorate(retryDecision, retryResponse, seq, System.currentTimeMillis() - start, model);
                    }
                }
            }
            if (response.getContent() == null || response.getContent().isBlank()) {
                throw new BusinessException("专家决策模型返回空内容 [" + agentName + "]，请重试");
            }
            decision = new LinkedHashMap<>();
            decision.put("action", "final");
            decision.put("answer", response.getContent());
        }
        return decorate(decision, response, seq, System.currentTimeMillis() - start, model);
    }

    private Map<String, Object> decorate(Map<String, Object> decision, ChatResponse response,
                                         int seq, long durationMs, AiModelConfig model) {
        decision.put("__response", response);
        decision.put("__raw", response.getContent());
        decision.put("__seq", seq);
        decision.put("__durationMs", durationMs);
        decision.put("__modelId", model.getId());
        return decision;
    }

    private Map<String, Object> finalDecision(String answer) {
        Map<String, Object> decision = new LinkedHashMap<>();
        decision.put("action", "final");
        decision.put("answer", answer);
        return decision;
    }

    private String buildExpertPrompt(AgentGraphContext ctx, AgentGraphState state, AiAgentConfig agent,
                                     String task, List<String> allowedTools, String agentCode) {
        StringBuilder sb = new StringBuilder();
        sb.append(agent.getSystemPrompt()).append('\n');
        sb.append("可用工具：\n").append(ctx.getToolRegistry().describeTools(allowedTools)).append('\n');
        String tables = tableSchemaInspector.describeTables();
        if (!tables.isBlank()) {
            sb.append("数据库可用表（编写 SQL 必须使用以下真实表名，禁止臆造）：\n").append(tables).append('\n');
        }
        String userTemplate = agent.getUserPromptTemplate();
        sb.append(userTemplate == null || userTemplate.isBlank() ? task
                : userTemplate.replace("{input}", task)).append('\n');
        String history = expertToolHistory(ctx, state, agentCode);
        if (!history.isBlank()) {
            sb.append("已执行的工具与结果：\n").append(history).append('\n');
        }
        sb.append("""

                请以严格 JSON 输出决策（不要包含其他文字、不要用 markdown 代码块包裹）：
                1. 需要调用工具：{"action":"tool","calls":[{"tool":"工具名","args":{参数}}],"thought":"原因"}
                2. 可以回答：{"action":"final","answer":"完整回答"}
                """);
        return sb.toString();
    }

    private String expertToolHistory(AgentGraphContext ctx, AgentGraphState state, String agentCode) {
        List<?> history = state.value(AgentGraphState.TOOL_HISTORY)
                .map(v -> (List<?>) v).orElse(List.of());
        StringBuilder sb = new StringBuilder();
        for (Object h : history) {
            if (h instanceof Map<?, ?> record && agentCode.equals(record.get("agentCode"))) {
                sb.append("- ").append(record.get("tool")).append(" → ").append(record.get("result")).append('\n');
            }
        }
        return sb.toString();
    }

    private String recentToolHistory(AgentGraphContext ctx, AgentGraphState state, String agentCode) {
        String history = expertToolHistory(ctx, state, agentCode);
        return history.isBlank() ? "无" : decisionJsonParser.truncate(history);
    }

    /**
     * 解析成员 Agent 的决策模型绑定：{"decision":{"modelId":..}|{"modelType":"text"}}
     */
    private AiModelConfig resolveDecisionModel(AiAgentConfig agent) {
        try {
            if (agent.getModelBindings() == null || agent.getModelBindings().isBlank()) {
                return null;
            }
            JsonNode binding = objectMapper.readTree(agent.getModelBindings()).path("decision");
            if (binding.has("modelId")) {
                return modelConfigMapper.selectById(binding.get("modelId").asLong());
            }
            if (binding.has("modelType")) {
                return modelConfigMapper.selectList(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AiModelConfig>()
                                .eq(AiModelConfig::getModelType, binding.get("modelType").asText())
                                .eq(AiModelConfig::getStatus, 1)
                                .orderByAsc(AiModelConfig::getId)
                                .last("LIMIT 1"))
                        .stream().findFirst().orElse(null);
            }
        } catch (Exception e) {
            log.warn("解析专家模型绑定失败 agentId={}: {}", agent.getId(), e.getMessage());
        }
        return null;
    }

    // ========== 专家审批节点（高危工具中断，复用单 Agent 中断机制） ==========

    private org.bsc.langgraph4j.action.NodeActionWithConfig<AgentGraphState> expertApprovalNode(AgentGraphContext ctx) {
        return (state, config) -> {
            String approval = state.value(AgentGraphState.EXPERT_APPROVAL)
                    .map(String::valueOf).orElse("");
            if (!"APPROVED".equals(approval) && !"REJECTED".equals(approval)) {
                String detail = describePendingCalls(state);
                String agentName = dispatchedAgentName(state);
                ctx.emit(AgentEvents.Type.STEP_END, NODE_EXPERT_APPROVAL, "[" + agentName + "] 人工确认", Map.of(
                        "seq", ctx.nextSeq(), "status", "INTERRUPTED",
                        "output", "高危工具调用，等待人工确认：" + detail));
                throw new org.bsc.langgraph4j.GraphInterruptException(config,
                        "专家 [" + agentName + "] 判定需要执行高危工具，请人工确认：\n" + detail);
            }
            return Map.of();
        };
    }

    private String dispatchedAgentName(AgentGraphState state) {
        return state.value(AgentGraphState.CURRENT_DISPATCH)
                .map(v -> (Map<?, ?>) v)
                .map(d -> str(d, "agentName", "专家"))
                .orElse("专家");
    }

    private String describePendingCalls(AgentGraphState state) {
        Object decisionObj = state.value(AgentGraphState.DECISION).orElse(null);
        if (!(decisionObj instanceof Map<?, ?> decision)
                || !(decision.get("calls") instanceof List<?> calls) || calls.isEmpty()) {
            return "（无调用明细）";
        }
        StringBuilder sb = new StringBuilder();
        for (Object call : calls) {
            if (call instanceof Map<?, ?> c) {
                sb.append("工具: ").append(c.get("tool"));
                if (c.get("args") != null) {
                    try {
                        sb.append("\n参数: ").append(objectMapper.writeValueAsString(c.get("args")));
                    } catch (Exception ignored) {
                        sb.append("\n参数: ").append(c.get("args"));
                    }
                }
                sb.append("\n\n");
            }
        }
        return sb.toString().trim();
    }

    // ========== 专家工具执行节点（白名单校验 + 并行执行） ==========

    private AsyncNodeAction<AgentGraphState> expertToolsNode(AgentGraphContext ctx) {
        return AsyncNodeAction.node_async(state -> {
            Map<?, ?> decision = state.value(AgentGraphState.DECISION)
                    .map(v -> (Map<?, ?>) v).orElse(Map.of());
            Map<?, ?> dispatch = state.value(AgentGraphState.CURRENT_DISPATCH)
                    .map(v -> (Map<?, ?>) v).orElse(Map.of());
            String agentCode = str(dispatch, "agentCode");
            String agentName = str(dispatch, "agentName", agentCode);
            @SuppressWarnings("unchecked")
            List<String> allowedTools = dispatch.get("allowedTools") instanceof List<?> l
                    ? (List<String>) l : List.of();
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
                ctx.emit(AgentEvents.Type.TOOL_CALL, NODE_EXPERT_TOOLS, toolName, Map.of(
                        "seq", callSeq, "args", args, "agentCode", agentCode, "agentName", agentName));
                futures.add(CompletableFuture.runAsync(() -> {
                    long toolStart = System.currentTimeMillis();
                    Map<String, Object> record = new LinkedHashMap<>();
                    record.put("tool", toolName);
                    record.put("args", args);
                    record.put("agentCode", agentCode);
                    if (!ctx.getToolRegistry().isAllowed(toolName, allowedTools)) {
                        record.put("error", "工具不在该专家白名单内: " + toolName);
                        record.put("status", "FAILED");
                        ctx.emit(AgentEvents.Type.TOOL_RESULT, NODE_EXPERT_TOOLS, toolName, Map.of(
                                "seq", callSeq, "status", "FAILED",
                                "error", "工具不在该专家白名单内: " + toolName,
                                "durationMs", System.currentTimeMillis() - toolStart,
                                "agentCode", agentCode, "agentName", agentName));
                    } else {
                        try {
                            Object result = ctx.getToolRegistry().get(toolName).execute(args);
                            record.put("result", decisionJsonParser.truncate(String.valueOf(result)));
                            record.put("status", "SUCCESS");
                            ctx.emit(AgentEvents.Type.TOOL_RESULT, NODE_EXPERT_TOOLS, toolName, Map.of(
                                    "seq", callSeq, "status", "SUCCESS",
                                    "output", decisionJsonParser.truncate(String.valueOf(result)),
                                    "durationMs", System.currentTimeMillis() - toolStart,
                                    "agentCode", agentCode, "agentName", agentName));
                        } catch (Exception e) {
                            record.put("error", e.getMessage());
                            record.put("status", "FAILED");
                            ctx.emit(AgentEvents.Type.TOOL_RESULT, NODE_EXPERT_TOOLS, toolName, Map.of(
                                    "seq", callSeq, "status", "FAILED", "error", e.getMessage(),
                                    "durationMs", System.currentTimeMillis() - toolStart,
                                    "agentCode", agentCode, "agentName", agentName));
                        }
                    }
                    synchronized (newHistory) {
                        newHistory.add(record);
                    }
                }));
            }
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            history.addAll(newHistory);
            ctx.emit(AgentEvents.Type.STEP_END, NODE_EXPERT_TOOLS, "[" + agentName + "] 工具执行", Map.of(
                    "seq", ctx.nextSeq(), "status", "SUCCESS",
                    "calls", newHistory.size(), "durationMs", System.currentTimeMillis() - start,
                    "agentCode", agentCode, "agentName", agentName));
            return Map.of(AgentGraphState.TOOL_HISTORY, history);
        });
    }

    // ========== 专家汇报节点 ==========

    private AsyncNodeAction<AgentGraphState> expertReportNode(AgentGraphContext ctx) {
        return AsyncNodeAction.node_async(state -> {
            Map<?, ?> dispatch = state.value(AgentGraphState.CURRENT_DISPATCH)
                    .map(v -> (Map<?, ?>) v).orElse(Map.of());
            String agentCode = str(dispatch, "agentCode");
            if (agentCode.isBlank() || "null".equals(agentCode)) {
                return Map.of();
            }
            String agentName = str(dispatch, "agentName", agentCode);
            String task = str(dispatch, "task");
            String approval = state.value(AgentGraphState.EXPERT_APPROVAL)
                    .map(String::valueOf).orElse("");
            String output;
            if ("REJECTED".equals(approval)) {
                output = "本轮包含高危操作，已被人工审核驳回，未执行。";
            } else {
                Map<?, ?> decision = state.value(AgentGraphState.DECISION)
                        .map(v -> (Map<?, ?>) v).orElse(Map.of());
                output = "final".equals(String.valueOf(decision.get("action")))
                        ? String.valueOf(decision.get("answer")) : "（专家提前结束，未产出结论）";
            }
            List<Map<String, Object>> reports = state.value(AgentGraphState.AGENT_REPORTS)
                    .map(v -> (List<Map<String, Object>>) v).orElse(new ArrayList<>());
            Map<String, Object> report = new LinkedHashMap<>();
            report.put("agent", agentName);
            report.put("agentCode", agentCode);
            report.put("task", task);
            report.put("output", output);
            reports.add(report);
            ctx.emit(AgentEvents.Type.STEP_END, NODE_EXPERT_REPORT, "[" + agentName + "] 汇报", Map.of(
                    "seq", ctx.nextSeq(), "status", "SUCCESS",
                    "output", decisionJsonParser.truncate(output),
                    "agentCode", agentCode, "agentName", agentName));
            // 清空本轮专家上下文：分派、审批结论、步数（防空串到下一专家）
            return Map.of(
                    AgentGraphState.AGENT_REPORTS, reports,
                    AgentGraphState.CURRENT_DISPATCH, "",
                    AgentGraphState.EXPERT_APPROVAL, "",
                    AgentGraphState.EXPERT_STEPS, 0);
        });
    }

    // ========== 总结节点（Supervisor 直出 或 流式总结） ==========

    private AsyncNodeAction<AgentGraphState> summaryNode(AgentGraphContext ctx) {
        return AsyncNodeAction.node_async(state -> {
            String supervisorAnswer = state.value(AgentGraphState.SUPERVISOR_ANSWER)
                    .map(String::valueOf).orElse("");
            if (!supervisorAnswer.isBlank()) {
                ctx.emit(AgentEvents.Type.CONTENT, NODE_SUMMARY, "总结回答", Map.of("delta", supervisorAnswer));
                ctx.emit(AgentEvents.Type.STEP_END, NODE_SUMMARY, "总结回答", Map.of(
                        "seq", ctx.nextSeq(), "status", "SUCCESS",
                        "output", decisionJsonParser.truncate(supervisorAnswer)));
                return Map.of(AgentGraphState.FINAL_ANSWER, supervisorAnswer);
            }
            AiModelConfig model = ctx.getNodeModels().get(KEY_SUMMARY);
            if (model == null) {
                model = ctx.getNodeModels().get(KEY_SUPERVISOR);
            }
            if (model == null) {
                throw new BusinessException("总结节点未绑定模型（团队 model_bindings.summary）");
            }
            int seq = ctx.nextSeq();
            long start = System.currentTimeMillis();
            ctx.emit(AgentEvents.Type.STEP_START, NODE_SUMMARY, "总结回答", Map.of(
                    "seq", seq, "model", model.getModelName()));
            StringBuilder sb = new StringBuilder();
            sb.append("用户任务：").append(state.value(AgentGraphState.INPUT)
                    .map(String::valueOf).orElse("")).append('\n');
            List<?> reports = state.value(AgentGraphState.AGENT_REPORTS)
                    .map(v -> (List<?>) v).orElse(List.of());
            sb.append("各专家工作汇报：\n");
            for (Object r : reports) {
                sb.append("- ").append(r).append('\n');
            }
            sb.append("请综合以上专家汇报，给出完整、准确的最终回答。");

            ChatRequest request = new ChatRequest();
            request.setContent(sb.toString());
            request.setTemperature(0.5);
            StringBuilder answerBuf = new StringBuilder();
            llmClient.stream(request, model)
                    .doOnNext(frame -> {
                        String delta = extractContentDelta(frame);
                        if (delta != null && !delta.isEmpty()) {
                            answerBuf.append(delta);
                            ctx.emit(AgentEvents.Type.CONTENT, NODE_SUMMARY, "总结回答", Map.of("delta", delta));
                        }
                    })
                    .blockLast(java.time.Duration.ofMinutes(3));
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
                    "seq", seq, "status", "SUCCESS",
                    "output", decisionJsonParser.truncate(answer),
                    "durationMs", System.currentTimeMillis() - start,
                    "modelId", model.getId()));
            return Map.of(AgentGraphState.FINAL_ANSWER, answer);
        });
    }

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

    private int nullToZero(Integer value) {
        return value == null ? 0 : value;
    }

    /** Map<?,?> 安全取字符串（getOrDefault 与通配符类型不兼容） */
    private String str(Map<?, ?> map, String key) {
        Object value = map.get(key);
        return value == null ? "" : String.valueOf(value);
    }

    private String str(Map<?, ?> map, String key, String defaultValue) {
        Object value = map.get(key);
        return value == null ? defaultValue : String.valueOf(value);
    }
}
