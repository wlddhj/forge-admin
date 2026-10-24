package com.forge.modules.ai.agent.graph;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.forge.common.exception.BusinessException;
import com.forge.modules.ai.agent.AgentEvents;
import com.forge.modules.ai.agent.kernel.AgentGraphContext;
import com.forge.modules.ai.agent.kernel.AgentGraphDefinition;
import com.forge.modules.ai.agent.kernel.AgentGraphState;
import com.forge.modules.ai.agent.kernel.MysqlCheckpointSaver;
import com.forge.modules.ai.agent.kernel.RunScopedCache;
import com.forge.modules.ai.client.LlmClient;
import com.forge.modules.ai.client.NativeToolCalling;
import com.forge.modules.ai.dto.request.ChatRequest;
import com.forge.modules.ai.dto.response.ChatResponse;
import com.forge.modules.ai.entity.AiAgentConfig;
import com.forge.modules.ai.entity.AiAgentTeamMember;
import com.forge.modules.ai.entity.AiModelConfig;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.CompileConfig;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphInput;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.action.AsyncEdgeAction;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Semaphore;
import java.util.function.Consumer;

/**
 * Supervisor 调度式多 Agent 协作图（并行分派版）：
 * multimodal → supervisor（批量分派 calls[]，兼容单分派）→ expert_pool（独立专家子图并行执行，
 * 并发度 forge.ai.agent.expert-parallelism）→ supervisor 循环 → summary（流式总结）。
 * 专家为运行时构建的子图（decide→approval→tools 循环→report），独立 checkpoint 线程
 * （run-{runId}-exp-{round}-{i}）：中断恢复时已完成专家凭 EXPERT_DONE 标记跳过重跑，
 * 被中断专家从子图检查点续跑——审批通过/驳回作用于该 run 全部待确认专家。
 */
@Slf4j
@Component
public class SupervisorTeamGraph implements AgentGraphDefinition {

    public static final String GRAPH_KEY = "team-supervisor";
    public static final String NODE_MULTIMODAL = MultimodalSupport.NODE_MULTIMODAL;
    public static final String NODE_SUPERVISOR = "supervisor";
    public static final String NODE_EXPERT_POOL = "expert_pool";
    public static final String NODE_EXPERT_DECIDE = "expert_decide";
    public static final String NODE_EXPERT_APPROVAL = "expert_approval";
    public static final String NODE_EXPERT_TOOLS = "expert_tools";
    public static final String NODE_EXPERT_REPORT = "expert_report";
    public static final String NODE_SUMMARY = "summary";

    private static final String KEY_SUPERVISOR = "supervisor";
    private static final String KEY_SUMMARY = "summary";

    /** 子图内：专家最终汇报文本（expert_pool 读取后并入 AGENT_REPORTS） */
    private static final String SUB_REPORT = "subReport";
    /** 子图内：分派上下文（独立于外层，避免并行专家互染） */
    private static final String SUB_DISPATCH = "subDispatch";

    private final LlmClient llmClient;
    private final ObjectMapper objectMapper;
    private final DecisionJsonParser decisionJsonParser;
    private final MultimodalSupport multimodalSupport;
    private final com.forge.modules.ai.agent.tools.TableSchemaInspector tableSchemaInspector;
    private final PromptBudgeter promptBudgeter;
    private final NativeToolCalling nativeToolCalling;
    private final com.forge.modules.ai.agent.AgentProperties agentProperties;
    private final Executor agentExecutor;
    private final com.forge.modules.ai.config.AiModuleConfig aiModuleConfig;

    public SupervisorTeamGraph(LlmClient llmClient,
                               ObjectMapper objectMapper,
                               DecisionJsonParser decisionJsonParser,
                               MultimodalSupport multimodalSupport,
                               com.forge.modules.ai.agent.tools.TableSchemaInspector tableSchemaInspector,
                               PromptBudgeter promptBudgeter,
                               NativeToolCalling nativeToolCalling,
                               com.forge.modules.ai.agent.AgentProperties agentProperties,
                               @Qualifier("agentExecutor") Executor agentExecutor,
                               com.forge.modules.ai.config.AiModuleConfig aiModuleConfig) {
        this.llmClient = llmClient;
        this.objectMapper = objectMapper;
        this.decisionJsonParser = decisionJsonParser;
        this.multimodalSupport = multimodalSupport;
        this.tableSchemaInspector = tableSchemaInspector;
        this.promptBudgeter = promptBudgeter;
        this.nativeToolCalling = nativeToolCalling;
        this.agentProperties = agentProperties;
        this.agentExecutor = agentExecutor;
        this.aiModuleConfig = aiModuleConfig;
    }

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
                .addNode(NODE_MULTIMODAL, multimodalSupport.node(ctx))
                .addNode(NODE_SUPERVISOR, supervisorNode(ctx))
                .addNode(NODE_EXPERT_POOL, expertPoolNode(ctx))
                .addNode(NODE_SUMMARY, summaryNode(ctx))
                .addEdge(StateGraph.START, NODE_MULTIMODAL)
                .addEdge(NODE_MULTIMODAL, NODE_SUPERVISOR)
                .addConditionalEdges(NODE_SUPERVISOR, AsyncEdgeAction.edge_async(state -> {
                    Map<?, ?> decision = state.value(AgentGraphState.DECISION)
                            .map(v -> (Map<?, ?>) v).orElse(Map.of());
                    int rounds = state.value(AgentGraphState.STEPS).map(v -> (Integer) v).orElse(0);
                    int maxRounds = ctx.getAgentConfig().getMaxSteps() == null ? 6 : ctx.getAgentConfig().getMaxSteps();
                    if ("dispatch".equals(decision.get("action")) && rounds >= maxRounds) {
                        // 轮次达上限强制收束:可见提示(不再静默)
                        ctx.emit(AgentEvents.Type.STEP_END, NODE_SUPERVISOR, "调度决策", Map.of(
                                "seq", ctx.nextSeq(), "status", "SKIPPED",
                                "output", "已达最大调度轮次 " + maxRounds + ",基于现有汇报总结回答"));
                    }
                    return "dispatch".equals(decision.get("action")) && rounds < maxRounds
                            ? "experts" : "summary";
                }), Map.of("experts", NODE_EXPERT_POOL, "summary", NODE_SUMMARY))
                .addEdge(NODE_EXPERT_POOL, NODE_SUPERVISOR)
                .addEdge(NODE_SUMMARY, StateGraph.END);
    }

    // ========== Supervisor 调度节点 ==========

    /** 成员条目：成员配置 + 引用的 Agent 配置（Agent 不存在/未启用时为 null） */
    private record MemberEntry(AiAgentTeamMember member, AiAgentConfig agent) {
    }

    /** 成员索引（run 级批量构建一次，存 ctx 属性缓存整 run 复用，消灭循环内查库） */
    @SuppressWarnings("unchecked")
    private Map<String, MemberEntry> memberIndex(AgentGraphContext ctx) {
        return (Map<String, MemberEntry>) ctx.computeAttachmentIfAbsent("memberIndex", () -> {
            RunScopedCache runCache = ctx.getRunCache();
            Map<String, MemberEntry> index = new LinkedHashMap<>();
            for (AiAgentTeamMember m : ctx.getTeamMembers()) {
                AiAgentConfig agent = runCache.agent(m.getAgentId());
                if (agent != null && agent.getStatus() != null && agent.getStatus() == 1) {
                    index.put(agent.getAgentCode(), new MemberEntry(m, agent));
                }
            }
            return index;
        });
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

            Map<String, MemberEntry> index = memberIndex(ctx);
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
            // 批量/单条分派统一构建队列（独立子任务并行执行）
            List<Map<String, Object>> queue = buildDispatchQueue(decision, index);
            if (queue.isEmpty()) {
                throw new BusinessException("分派的专家不在团队清单内: " + decision.get("agent"));
            }
            for (Map<String, Object> dispatch : queue) {
                ctx.emit(AgentEvents.Type.STEP_END, NODE_SUPERVISOR, "任务分派", Map.of(
                        "seq", ctx.nextSeq(), "status", "SUCCESS",
                        "output", "分派给 [" + dispatch.get("agentName") + "]：" + dispatch.get("task"),
                        "agentCode", dispatch.get("agentCode"), "agentName", dispatch.get("agentName")));
            }
            int round = state.value(AgentGraphState.EXPERT_ROUND).map(v -> (Integer) v).orElse(0);
            return Map.of(
                    AgentGraphState.DECISION, decision,
                    AgentGraphState.STEPS, rounds + 1,
                    AgentGraphState.DISPATCH_QUEUE, queue,
                    AgentGraphState.EXPERT_ROUND, round + 1,
                    AgentGraphState.EXPERT_APPROVAL, "");
        });
    }

    /** 决策 → 分派队列：兼容单分派 {agent,task} 与批量 {calls:[{agent,task},...]} */
    private List<Map<String, Object>> buildDispatchQueue(Map<String, Object> decision,
                                                         Map<String, MemberEntry> index) {
        List<Map<String, Object>> queue = new ArrayList<>();
        if (decision.get("calls") instanceof List<?> calls) {
            for (Object raw : calls) {
                if (raw instanceof Map<?, ?> call) {
                    addDispatch(queue, index, String.valueOf(call.get("agent")),
                            String.valueOf(call.get("task")));
                }
            }
        }
        if (queue.isEmpty()) {
            addDispatch(queue, index, String.valueOf(decision.get("agent")),
                    String.valueOf(decision.get("task")));
        }
        return queue;
    }

    private void addDispatch(List<Map<String, Object>> queue, Map<String, MemberEntry> index,
                             String agentCode, String task) {
        MemberEntry entry = index.get(agentCode);
        if (entry == null) {
            return;
        }
        Map<String, Object> dispatch = new LinkedHashMap<>();
        dispatch.put("agentId", entry.member().getAgentId());
        dispatch.put("agentCode", entry.agent().getAgentCode());
        dispatch.put("agentName", entry.agent().getAgentName());
        dispatch.put("task", task == null || "null".equals(task) ? "" : task);
        dispatch.put("maxSteps", entry.member().getMaxSteps() == null ? 8 : entry.member().getMaxSteps());
        dispatch.put("allowedTools", parseAllowedTools(entry.member()));
        queue.add(dispatch);
    }

    private Map<String, Object> decideSupervisor(AgentGraphContext ctx, AgentGraphState state,
                                                 AiModelConfig model, Map<String, MemberEntry> index, int retry) {
        String prompt = buildSupervisorPrompt(ctx, state, index);
        ChatRequest request = new ChatRequest();
        request.setContent(prompt);
        request.setTemperature(0.1);
        ChatResponse response = llmClient.chatForDecision(request, model,
                delta -> ctx.emit(AgentEvents.Type.THOUGHT, NODE_SUPERVISOR, "调度决策", Map.of("delta", delta)));
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
                ChatResponse retryResponse = llmClient.chatForDecision(retryRequest, model, null);
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
        state.value(AgentGraphState.IMAGE_TEXT)
                .map(String::valueOf).filter(t -> !t.isBlank())
                .ifPresent(t -> sb.append("图片识别结果：").append(t).append('\n'));
        String memory = state.value(AgentGraphState.CONVERSATION_MEMORY)
                .map(String::valueOf).orElse("");
        String renderedMemory = promptBudgeter.renderMemory(memory);
        if (!renderedMemory.isBlank()) {
            sb.append("先前对话结论（同会话历史，供参考）：\n").append(renderedMemory).append('\n');
        }
        List<?> reports = state.value(AgentGraphState.AGENT_REPORTS)
                .map(v -> (List<?>) v).orElse(List.of());
        if (!reports.isEmpty()) {
            sb.append("已完成的专家汇报：\n").append(promptBudgeter.renderReports(reports));
        }
        sb.append("""

                请以严格 JSON 输出决策（不要包含其他文字、不要用 markdown 代码块包裹）：
                1. 需要分派专家执行：{"action":"dispatch","calls":[{"agent":"专家code","task":"明确的子任务指令"},...],"thought":"原因"}（多个互相独立的子任务可放在 calls 中一次批量分派，将并行执行；有依赖关系的子任务请分轮分派）
                2. 任务已完成可回答：{"action":"final","answer":"综合所有专家汇报的完整回答"}
                注意：agent 必须取自上述团队专家清单中的 code；信息不足以回答时优先分派专家。
                """);
        return sb.toString();
    }

    /**
     * 解析 Supervisor 决策：dispatch 的 agent 必须在成员清单内（批量时至少一个有效），否则视为解析失败
     */
    private Map<String, Object> parseSupervisorDecision(Map<String, MemberEntry> index, String content) {
        if (content == null) {
            return null;
        }
        for (JsonNode node : decisionJsonParser.extractJsonObjects(content.trim())) {
            String action = node.path("action").asText("");
            if ("dispatch".equals(action)) {
                boolean batch = node.path("calls").isArray() && node.path("calls").size() > 0;
                if (batch) {
                    boolean anyValid = false;
                    for (JsonNode call : node.path("calls")) {
                        String agent = call.path("agent").asText("");
                        if (!agent.isBlank() && index.containsKey(agent)
                                && !call.path("task").asText("").isBlank()) {
                            anyValid = true;
                            break;
                        }
                    }
                    if (!anyValid) {
                        return null;
                    }
                    Map<String, Object> decision = new LinkedHashMap<>();
                    decision.put("action", "dispatch");
                    decision.put("calls", objectMapper.convertValue(node.path("calls"), List.class));
                    decision.put("thought", node.path("thought").asText(""));
                    return decision;
                }
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

    // ========== 专家池节点（并行执行专家子图 + 中断聚合 + 恢复分类） ==========

    /** 单个专家子图的执行结果 */
    private record SubResult(Map<String, Object> report, String interruptDetail) {
        boolean interrupted() {
            return interruptDetail != null;
        }
    }

    private AsyncNodeAction<AgentGraphState> expertPoolNode(AgentGraphContext ctx) {
        return AsyncNodeAction.node_async(state -> {
            List<?> queue = state.value(AgentGraphState.DISPATCH_QUEUE)
                    .map(v -> (List<?>) v).orElse(List.of());
            if (queue.isEmpty()) {
                return Map.of();
            }
            int round = state.value(AgentGraphState.EXPERT_ROUND).map(v -> (Integer) v).orElse(0);
            String approval = state.value(AgentGraphState.EXPERT_APPROVAL)
                    .map(String::valueOf).orElse("");
            long start = System.currentTimeMillis();
            ctx.emit(AgentEvents.Type.STEP_START, NODE_EXPERT_POOL, "专家池", Map.of(
                    "seq", ctx.nextSeq(), "experts", queue.size(), "round", round,
                    "parallelism", Math.max(1, agentProperties.getExpertParallelism())));

            int parallelism = Math.max(1, agentProperties.getExpertParallelism());
            Semaphore permits = new Semaphore(parallelism);
            List<CompletableFuture<SubResult>> futures = new ArrayList<>();
            for (int i = 0; i < queue.size(); i++) {
                Map<?, ?> dispatch = (Map<?, ?>) queue.get(i);
                String threadKey = subThreadId(ctx.getRunId(), round, i);
                futures.add(CompletableFuture.supplyAsync(() -> {
                    try {
                        permits.acquire();
                        return runExpertSubgraph(ctx, dispatch, threadKey, approval, state);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return null;
                    } catch (Exception e) {
                        log.error("专家子图执行失败 threadKey={}: {}", threadKey, e.getMessage(), e);
                        return new SubResult(degradedReport(dispatch, "专家执行异常: " + e.getMessage()), null);
                    } finally {
                        permits.release();
                    }
                }, agentExecutor));
            }
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            List<Map<String, Object>> reports = state.value(AgentGraphState.AGENT_REPORTS)
                    .map(v -> (List<Map<String, Object>>) v).orElse(new ArrayList<>());
            List<String> interruptDetails = new ArrayList<>();
            for (CompletableFuture<SubResult> future : futures) {
                SubResult result = future.join();
                if (result == null) {
                    continue;
                }
                if (result.interrupted()) {
                    interruptDetails.add(result.interruptDetail());
                } else if (result.report() != null) {
                    reports.add(result.report());
                }
            }
            // 任一专家待审批：聚合详情中断整图（已完成专家的 EXPERT_DONE 标记已写入，恢复时不重跑）
            if (!interruptDetails.isEmpty()) {
                String detail = String.join("\n\n", interruptDetails);
                ctx.emit(AgentEvents.Type.STEP_END, NODE_EXPERT_POOL, "专家池", Map.of(
                        "seq", ctx.nextSeq(), "status", "INTERRUPTED",
                        "output", "高危工具调用，等待人工确认：" + detail));
                throw new org.bsc.langgraph4j.GraphInterruptException(
                        RunnableConfig.builder().threadId("run-" + ctx.getRunId()).build(),
                        "专家判定需要执行高危工具，请人工确认（通过/驳回作用于本轮全部待确认专家）：\n" + detail);
            }
            ctx.emit(AgentEvents.Type.STEP_END, NODE_EXPERT_POOL, "专家池", Map.of(
                    "seq", ctx.nextSeq(), "status", "SUCCESS",
                    "calls", reports.size(), "durationMs", System.currentTimeMillis() - start));
            return Map.of(
                    AgentGraphState.AGENT_REPORTS, reports,
                    AgentGraphState.DISPATCH_QUEUE, new ArrayList<>(),
                    AgentGraphState.EXPERT_APPROVAL, "");
        });
    }

    private String subThreadId(Long runId, int round, int index) {
        return "run-" + runId + "-exp-" + round + "-" + index;
    }

    /**
     * 运行（或恢复/跳过）单个专家子图：
     * 已有 EXPERT_DONE 标记 → 直接复用汇报；有检查点无标记 → 中断续跑（注入审批结论）；
     * 无检查点 → 全新执行。子图 END 后写 EXPERT_DONE 标记（含汇报，恢复依据）。
     */
    private SubResult runExpertSubgraph(AgentGraphContext ctx, Map<?, ?> dispatch,
                                        String threadKey, String approval, AgentGraphState outerState) throws Exception {
        // 恢复分类
        var done = loadDoneReport(ctx, threadKey);
        if (done.isPresent()) {
            log.info("专家已完成（标记复用）threadKey={}", threadKey);
            return new SubResult(done.get(), null);
        }
        boolean resume = ctx.subSaver(threadKey).hasAnyCheckpoint(threadKey);

        // 构建子图
        StateGraph<AgentGraphState> subGraph = buildExpertSubgraph(ctx);
        MysqlCheckpointSaver saver = ctx.subSaver(threadKey);
        int maxSteps = dispatch.get("maxSteps") instanceof Number n ? n.intValue() : 8;
        CompiledGraph<AgentGraphState> app = subGraph.compile(CompileConfig.builder()
                .checkpointSaver(saver)
                .recursionLimit(maxSteps * 3)
                .releaseThread(false)
                .build());
        RunnableConfig config = RunnableConfig.builder().threadId(threadKey).build();

        Map<String, Object> report = buildReportSkeleton(dispatch);
        boolean reachedEnd = false;
        String finalAnswer = null;
        try {
            GraphInput input;
            if (resume) {
                Map<String, Object> update = new LinkedHashMap<>();
                if ("APPROVED".equals(approval) || "REJECTED".equals(approval)) {
                    update.put(AgentGraphState.EXPERT_APPROVAL, approval);
                }
                input = GraphInput.resume(update);
            } else {
                input = GraphInput.args(subInitialState(outerState, dispatch));
            }
            var iterator = app.stream(input, config).iterator();
            while (iterator.hasNext()) {
                var out = iterator.next();
                if (out.isEND()) {
                    reachedEnd = true;
                    finalAnswer = out.state().value(SUB_REPORT).map(String::valueOf).orElse(null);
                }
            }
        } catch (RuntimeException e) {
            org.bsc.langgraph4j.GraphInterruptException interrupt = findInterrupt(e);
            if (interrupt != null) {
                // 中断：已完成的其他子运行照常落标记，本专家恢复时从此处续跑
                return new SubResult(null, "[" + report.get("agent") + "] " + interrupt.getMessage());
            }
            throw e;
        }
        if (!reachedEnd) {
            // 框架吞掉中断异常正常收流（动态中断）：本专家待审批，不写完成标记
            String reason = saver.lastInterruptReason(config)
                    .orElseGet(() -> "判定需要执行高危工具（明细见决策步骤）");
            return new SubResult(null, "[" + report.get("agent") + "] " + reason);
        }
        if (finalAnswer == null) {
            finalAnswer = "（专家未产出结论）";
        }
        report.put("output", finalAnswer);
        saver.markDone(threadKey, report);
        ctx.emit(AgentEvents.Type.STEP_END, NODE_EXPERT_REPORT, "[" + report.get("agent") + "] 汇报", Map.of(
                "seq", ctx.nextSeq(), "status", "SUCCESS",
                "output", decisionJsonParser.truncate(finalAnswer),
                "agentCode", report.get("agentCode"), "agentName", report.get("agent")));
        return new SubResult(report, null);
    }

    /** 单专家子图：decide →（approval→）tools 循环 → report */
    private StateGraph<AgentGraphState> buildExpertSubgraph(AgentGraphContext ctx)
            throws org.bsc.langgraph4j.GraphStateException {
        return new StateGraph<>(AgentGraphState::new)
                .addNode(NODE_EXPERT_DECIDE, expertDecideNode(ctx))
                .addNode(NODE_EXPERT_APPROVAL,
                        org.bsc.langgraph4j.action.AsyncNodeActionWithConfig.node_async(expertApprovalNode(ctx)))
                .addNode(NODE_EXPERT_TOOLS, expertToolsNode(ctx))
                .addNode(NODE_EXPERT_REPORT, expertReportNode(ctx))
                .addEdge(StateGraph.START, NODE_EXPERT_DECIDE)
                .addConditionalEdges(NODE_EXPERT_DECIDE, AsyncEdgeAction.edge_async(state -> {
                    Map<?, ?> decision = state.value(AgentGraphState.DECISION)
                            .map(v -> (Map<?, ?>) v).orElse(Map.of());
                    int expertSteps = state.value(AgentGraphState.EXPERT_STEPS).map(v -> (Integer) v).orElse(0);
                    int limit = state.value(SUB_DISPATCH)
                            .map(v -> (Map<?, ?>) v).map(d -> d.get("maxSteps"))
                            .filter(Objects::nonNull).map(v -> ((Number) v).intValue()).orElse(8);
                    boolean approved = "APPROVED".equals(state.value(AgentGraphState.EXPERT_APPROVAL)
                            .map(String::valueOf).orElse(""));
                    if (!"tool".equals(decision.get("action")) || expertSteps > limit
                            || !(decision.get("calls") instanceof List<?> calls) || calls.isEmpty()) {
                        return "done";
                    }
                    List<String> toolNames = calls.stream()
                            .map(c -> String.valueOf(((Map<?, ?>) c).get("tool"))).toList();
                    List<String> allowed = allowedToolsOf(state);
                    return !approved && ctx.getToolRegistry().hasHighRisk(toolNames) ? "approval" : "tools";
                }), Map.of("tools", NODE_EXPERT_TOOLS, "approval", NODE_EXPERT_APPROVAL, "done", NODE_EXPERT_REPORT))
                .addConditionalEdges(NODE_EXPERT_APPROVAL, AsyncEdgeAction.edge_async(state ->
                        "REJECTED".equals(state.value(AgentGraphState.EXPERT_APPROVAL)
                                .map(String::valueOf).orElse(""))
                                ? "rejected" : "approved"), Map.of(
                        "approved", NODE_EXPERT_TOOLS,
                        "rejected", NODE_EXPERT_REPORT))
                .addEdge(NODE_EXPERT_TOOLS, NODE_EXPERT_DECIDE)
                .addEdge(NODE_EXPERT_REPORT, StateGraph.END);
    }

    /** 子图初始状态：携带分派上下文 + 外层共享上下文（输入/图片识别/记忆）快照 */
    private Map<String, Object> subInitialState(AgentGraphState outerState, Map<?, ?> dispatch) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put(SUB_DISPATCH, new LinkedHashMap<>((Map<String, ?>) dispatch));
        state.put(AgentGraphState.INPUT, outerState.value(AgentGraphState.INPUT).map(String::valueOf).orElse(""));
        state.put(AgentGraphState.IMAGE_TEXT, outerState.value(AgentGraphState.IMAGE_TEXT)
                .map(String::valueOf).orElse(""));
        state.put(AgentGraphState.CONVERSATION_MEMORY, outerState.value(AgentGraphState.CONVERSATION_MEMORY)
                .map(String::valueOf).orElse(""));
        state.put(AgentGraphState.TOOL_HISTORY, new ArrayList<>());
        state.put(AgentGraphState.EXPERT_STEPS, 0);
        state.put(AgentGraphState.EXPERT_APPROVAL, "");
        return state;
    }

    // ========== 专家决策节点（子图内） ==========

    private AsyncNodeAction<AgentGraphState> expertDecideNode(AgentGraphContext ctx) {
        return AsyncNodeAction.node_async(state -> {
            Map<?, ?> dispatch = state.value(SUB_DISPATCH)
                    .map(v -> (Map<?, ?>) v).orElse(Map.of());
            String agentCode = str(dispatch, "agentCode");
            String agentName = str(dispatch, "agentName", agentCode);
            List<String> allowedTools = allowedToolsOf(state);
            int limit = dispatch.get("maxSteps") instanceof Number n ? n.intValue() : 8;
            int expertSteps = state.value(AgentGraphState.EXPERT_STEPS).map(v -> (Integer) v).orElse(0);

            AiAgentConfig agent = ctx.getRunCache().agent(
                    dispatch.get("agentId") instanceof Number n ? n.longValue() : -1L);
            // 降级路径：专家不可用或达步数上限，不调用模型直接产出汇报性结论
            String degraded = degradedReason(ctx, agent, limit, expertSteps);
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

    /** 降级原因（专家不可用 / 决策模型未绑定 / 达步数上限），可执行返回 null */
    private String degradedReason(AgentGraphContext ctx, AiAgentConfig agent, int limit, int expertSteps) {
        if (agent == null || agent.getStatus() == null || agent.getStatus() != 1) {
            return "（专家不可用：配置缺失或未启用）";
        }
        if (resolveDecisionModel(ctx, agent) == null) {
            return "（专家不可用：决策模型未绑定，需在其 Agent 配置 model_bindings.decision）";
        }
        if (expertSteps > limit) {
            return "（达到该专家步数上限，提前结束）";
        }
        return null;
    }

    private Map<String, Object> decideExpert(AgentGraphContext ctx, AgentGraphState state, AiAgentConfig agent,
                                             Map<?, ?> dispatch, List<String> allowedTools, int expertSteps, int retry) {
        String agentCode = str(dispatch, "agentCode");
        String agentName = str(dispatch, "agentName", agentCode);
        String task = str(dispatch, "task");
        AiModelConfig model = resolveDecisionModel(ctx, agent);
        int seq = ctx.nextSeq();
        ctx.emit(AgentEvents.Type.STEP_START, NODE_EXPERT_DECIDE, "[" + agentName + "] 决策", Map.of(
                "seq", seq, "model", model.getModelName(), "step", expertSteps + 1,
                "agentCode", agentCode, "agentName", agentName));
        long start = System.currentTimeMillis();

        Consumer<String> onDelta = delta -> ctx.emit(AgentEvents.Type.THOUGHT, NODE_EXPERT_DECIDE,
                "[" + agentName + "] 决策", Map.of("delta", delta));
        ChatResponse response = null;
        if (nativeToolCalling.enabled(model)) {
            try {
                response = nativeToolCalling.call(buildExpertNativePrompt(ctx, state, agent, task, allowedTools),
                        model, ctx.getToolRegistry(), allowedTools, onDelta);
            } catch (NativeToolCalling.ToolsUnsupportedException e) {
                log.info("[team-expert] native 不支持，回退 JSON 协议: {}", e.getMessage());
            } catch (Exception e) {
                log.warn("[team-expert] native 调用失败，回退 JSON 协议: {}", e.getMessage());
            }
        }
        if (response == null) {
            ChatRequest request = new ChatRequest();
            request.setContent(buildExpertPrompt(ctx, state, agent, task, allowedTools));
            request.setTemperature(0.1);
            response = llmClient.chatForDecision(request, model, onDelta);
        }
        if (!Boolean.TRUE.equals(response.getSuccess())) {
            throw new BusinessException("专家决策模型调用失败 [" + agentName + "]: " + response.getErrorMessage());
        }
        Map<String, Object> decision = decisionJsonParser.parseDecision(response.getContent());
        if (decision == null) {
            if (retry < 1) {
                ChatRequest retryRequest = new ChatRequest();
                retryRequest.setContent(buildExpertPrompt(ctx, state, agent, task, allowedTools)
                        + "\n\n注意：上一次输出无法解析为合法 JSON，请务必只输出一个完整合法的 JSON 对象。");
                retryRequest.setTemperature(0.1);
                ChatResponse retryResponse = llmClient.chatForDecision(retryRequest, model, onDelta);
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
                                     String task, List<String> allowedTools) {
        StringBuilder sb = new StringBuilder();
        sb.append(agent.getSystemPrompt()).append('\n');
        sb.append("可用工具：\n").append(ctx.getToolRegistry().describeTools(allowedTools)).append('\n');
        String tables = promptBudgeter.renderTables(task,
                tableSchemaInspector.enabledTables());
        if (!tables.isBlank()) {
            sb.append("数据库可用表（编写 SQL 必须使用以下真实表名，禁止臆造）：\n").append(tables).append('\n');
        }
        String userTemplate = agent.getUserPromptTemplate();
        sb.append(userTemplate == null || userTemplate.isBlank() ? task
                : userTemplate.replace("{input}", task)).append('\n');
        appendSharedContext(ctx, state, sb);
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

    /** native 轨专家提示词（无 JSON 协议说明，tools 结构化传递） */
    private ChatRequest buildExpertNativePrompt(AgentGraphContext ctx, AgentGraphState state, AiAgentConfig agent,
                                                String task, List<String> allowedTools) {
        StringBuilder sb = new StringBuilder();
        sb.append(agent.getSystemPrompt()).append('\n');
        String tables = promptBudgeter.renderTables(task, tableSchemaInspector.enabledTables());
        if (!tables.isBlank()) {
            sb.append("数据库可用表（编写 SQL 必须使用以下真实表名，禁止臆造）：\n").append(tables).append('\n');
        }
        String userTemplate = agent.getUserPromptTemplate();
        sb.append(userTemplate == null || userTemplate.isBlank() ? task
                : userTemplate.replace("{input}", task)).append('\n');
        appendSharedContext(ctx, state, sb);
        String history = promptBudgeter.renderToolHistory(
                state.value(AgentGraphState.TOOL_HISTORY).map(v -> (List<?>) v).orElse(List.of()), null);
        if (!history.isBlank()) {
            sb.append("已执行的工具与结果：\n").append(history);
        }
        sb.append("\n请决定：需要工具则调用对应工具（仅限分派允许的工具）；已可回答则直接给出完整回答。");
        ChatRequest request = new ChatRequest();
        request.setContent(sb.toString());
        request.setTemperature(0.1);
        return request;
    }

    /** 专家提示词共享段：图片识别结果 + 多轮记忆（取自子图状态快照） */
    private void appendSharedContext(AgentGraphContext ctx, AgentGraphState state, StringBuilder sb) {
        state.value(AgentGraphState.IMAGE_TEXT)
                .map(String::valueOf).filter(t -> !t.isBlank())
                .ifPresent(t -> sb.append("图片识别结果：").append(t).append('\n'));
        String memory = state.value(AgentGraphState.CONVERSATION_MEMORY)
                .map(String::valueOf).orElse("");
        String rendered = promptBudgeter.renderMemory(memory);
        if (!rendered.isBlank()) {
            sb.append("先前对话结论（同会话历史，供参考，勿重复执行已完成的查询）：\n")
                    .append(rendered).append('\n');
        }
    }

    /**
     * 解析成员 Agent 的决策模型绑定：{"decision":{"modelId":..}|{"modelType":"text"}}（经 run 级缓存）
     */
    private AiModelConfig resolveDecisionModel(AgentGraphContext ctx, AiAgentConfig agent) {
        try {
            if (agent.getModelBindings() == null || agent.getModelBindings().isBlank()) {
                return null;
            }
            JsonNode binding = objectMapper.readTree(agent.getModelBindings()).path("decision");
            RunScopedCache runCache = ctx.getRunCache();
            if (binding.has("modelId")) {
                return runCache.model(binding.get("modelId").asLong());
            }
            if (binding.has("modelType")) {
                return runCache.modelByType(binding.get("modelType").asText());
            }
        } catch (Exception e) {
            log.warn("解析专家模型绑定失败 agentId={}: {}", agent.getId(), e.getMessage());
        }
        return null;
    }

    // ========== 专家审批节点（子图内，高危工具中断） ==========

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
        return state.value(SUB_DISPATCH)
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

    // ========== 专家工具执行节点（子图内，白名单校验 + 并行执行） ==========

    private AsyncNodeAction<AgentGraphState> expertToolsNode(AgentGraphContext ctx) {
        return AsyncNodeAction.node_async(state -> {
            Map<?, ?> decision = state.value(AgentGraphState.DECISION)
                    .map(v -> (Map<?, ?>) v).orElse(Map.of());
            Map<?, ?> dispatch = state.value(SUB_DISPATCH)
                    .map(v -> (Map<?, ?>) v).orElse(Map.of());
            String agentCode = str(dispatch, "agentCode");
            String agentName = str(dispatch, "agentName", agentCode);
            List<String> allowedTools = allowedToolsOf(state);
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
                            Object result = ctx.getToolRegistry().execute(toolName, args,
                                    com.forge.modules.ai.agent.tools.ToolExecutionContext.of(ctx.getUserId()));
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
                }, agentExecutor));
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

    // ========== 专家汇报节点（子图内，产出 SUB_REPORT） ==========

    private AsyncNodeAction<AgentGraphState> expertReportNode(AgentGraphContext ctx) {
        return AsyncNodeAction.node_async(state -> {
            Map<?, ?> dispatch = state.value(SUB_DISPATCH)
                    .map(v -> (Map<?, ?>) v).orElse(Map.of());
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
            return Map.of(SUB_REPORT, output,
                    AgentGraphState.EXPERT_APPROVAL, "");
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
            sb.append("各专家工作汇报：\n").append(promptBudgeter.renderReports(reports));
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
                    "seq", seq, "status", "SUCCESS",
                    "output", decisionJsonParser.truncate(answer),
                    "durationMs", System.currentTimeMillis() - start,
                    "modelId", model.getId()));
            return Map.of(AgentGraphState.FINAL_ANSWER, answer);
        });
    }

    // ========== 辅助 ==========

    @SuppressWarnings("unchecked")
    private List<String> allowedToolsOf(AgentGraphState state) {
        return state.value(SUB_DISPATCH)
                .map(v -> (Map<?, ?>) v)
                .map(d -> d.get("allowedTools"))
                .filter(v -> v instanceof List<?>)
                .map(v -> (List<String>) v)
                .orElse(List.of());
    }

    private Map<String, Object> buildReportSkeleton(Map<?, ?> dispatch) {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("agent", str(dispatch, "agentName", str(dispatch, "agentCode")));
        report.put("agentCode", str(dispatch, "agentCode"));
        report.put("task", str(dispatch, "task"));
        report.put("output", "");
        return report;
    }

    private Map<String, Object> degradedReport(Map<?, ?> dispatch, String reason) {
        Map<String, Object> report = buildReportSkeleton(dispatch);
        report.put("output", reason);
        return report;
    }

    private java.util.Optional<Map<String, Object>> loadDoneReport(AgentGraphContext ctx, String threadKey) {
        try {
            return ctx.subSaver(threadKey).loadDone(threadKey)
                    .map(state -> {
                        Object report = state.get("report") == null ? state : state.get("report");
                        return report instanceof Map<?, ?> map ? (Map<String, Object>) map : null;
                    });
        } catch (Exception e) {
            return java.util.Optional.empty();
        }
    }

    private org.bsc.langgraph4j.GraphInterruptException findInterrupt(Throwable error) {
        Throwable cur = error;
        int depth = 0;
        while (cur != null && depth++ < 10) {
            if (cur instanceof org.bsc.langgraph4j.GraphInterruptException interrupt) {
                return interrupt;
            }
            cur = cur.getCause();
        }
        return null;
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

    private long summaryTimeoutMs() {
        Integer timeout = aiModuleConfig.getClient().getSummaryTimeout();
        return timeout == null || timeout <= 0 ? 60000L : timeout;
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
