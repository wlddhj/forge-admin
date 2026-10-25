package com.forge.modules.ai.agent.kernel;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.forge.common.exception.BusinessException;
import com.forge.modules.ai.agent.AgentEvents;
import com.forge.modules.ai.agent.graph.SupervisorTeamGraph;
import com.forge.modules.ai.agent.tools.ToolRegistry;
import com.forge.modules.ai.entity.AiAgentConfig;
import com.forge.modules.ai.entity.AiAgentRun;
import com.forge.modules.ai.entity.AiAgentRunStep;
import com.forge.modules.ai.entity.AiAgentTeam;
import com.forge.modules.ai.entity.AiAgentTeamMember;
import com.forge.modules.ai.entity.AiAgentTodo;
import com.forge.modules.ai.entity.AiModelConfig;
import com.forge.modules.ai.mapper.AiAgentConfigMapper;
import com.forge.modules.ai.mapper.AiAgentCheckpointMapper;
import com.forge.modules.ai.mapper.AiAgentRunMapper;
import com.forge.modules.ai.mapper.AiAgentRunStepMapper;
import com.forge.modules.ai.mapper.AiAgentTeamMapper;
import com.forge.modules.ai.mapper.AiAgentTeamMemberMapper;
import com.forge.modules.ai.mapper.AiAgentTodoMapper;
import com.forge.modules.ai.mapper.AiModelConfigMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.CompileConfig;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphInput;
import org.bsc.langgraph4j.NodeOutput;
import org.bsc.langgraph4j.RunnableConfig;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Agent 执行器：流式运行 / 中断（人工确认）/ 恢复，步骤事件推送与落库
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentGraphRunner {

    private final AgentGraphRegistry registry;
    private final ToolRegistry toolRegistry;
    private final com.forge.modules.ai.agent.AgentProperties agentProperties;
    private final com.forge.modules.ai.service.AiUsageRecorder usageRecorder;
    private final AiAgentConfigMapper agentConfigMapper;
    private final AiAgentRunMapper runMapper;
    private final AiAgentRunStepMapper runStepMapper;
    private final AiAgentTodoMapper todoMapper;
    private final AiAgentCheckpointMapper checkpointMapper;
    private final AiModelConfigMapper modelConfigMapper;
    private final AiAgentTeamMapper teamMapper;
    private final AiAgentTeamMemberMapper teamMemberMapper;
    private final ObjectMapper objectMapper;

    /**
     * 流式执行：返回最终状态（SUCCEEDED/WAITING_APPROVAL/FAILED）
     */
    public AiAgentRun runStream(AiAgentConfig agentConfig, Long conversationId, Long userId,
                                String triggerType, String input, List<String> images,
                                Consumer<AgentEvents.AgentEvent> sseEmitter) {
        AiAgentRun run = new AiAgentRun();
        run.setAgentId(agentConfig.getId());
        run.setConversationId(conversationId);
        run.setUserId(userId);
        run.setTriggerType(triggerType == null ? "CONVERSATION" : triggerType);
        run.setStatus(AiAgentRun.STATUS_RUNNING);
        run.setInputSummary(input != null && input.length() > 400 ? input.substring(0, 400) : input);
        run.setStartTime(LocalDateTime.now());
        runMapper.insert(run);

        try {
            Map<String, Object> state = initialState(input, images);
            String memory = buildConversationMemory(conversationId);
            if (memory != null) {
                state.put(AgentGraphState.CONVERSATION_MEMORY, memory);
            }
            return execute(run, agentConfig, GraphInput.args(state), sseEmitter, null, null);
        } catch (Exception e) {
            log.error("Agent 运行失败 runId={}", run.getId(), e);
            return finishRun(run, AiAgentRun.STATUS_FAILED, e.getMessage());
        }
    }

    /**
     * 团队协作流式执行：Supervisor 调度多个专家子 Agent（images 经 multimodal 节点识别后参与调度）
     */
    public AiAgentRun runTeamStream(AiAgentTeam team, Long conversationId, Long userId,
                                    String triggerType, String input, List<String> images,
                                    Consumer<AgentEvents.AgentEvent> sseEmitter) {
        List<AiAgentTeamMember> members = teamMemberMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AiAgentTeamMember>()
                        .eq(AiAgentTeamMember::getTeamId, team.getId())
                        .orderByAsc(AiAgentTeamMember::getSortOrder));
        if (members.isEmpty()) {
            throw new BusinessException("团队没有配置成员，无法执行协作");
        }

        AiAgentRun run = new AiAgentRun();
        run.setTeamId(team.getId());
        run.setConversationId(conversationId);
        run.setUserId(userId);
        run.setTriggerType(triggerType == null ? "CONVERSATION" : triggerType);
        run.setStatus(AiAgentRun.STATUS_RUNNING);
        run.setInputSummary(input != null && input.length() > 400 ? input.substring(0, 400) : input);
        run.setStartTime(LocalDateTime.now());
        runMapper.insert(run);

        Map<String, Object> state = initialState(input, images);
        state.put(AgentGraphState.TEAM_ID, team.getId());
        state.put(AgentGraphState.AGENT_REPORTS, new java.util.ArrayList<>());
        state.put(AgentGraphState.EXPERT_STEPS, 0);
        state.put(AgentGraphState.DISPATCH_QUEUE, new java.util.ArrayList<>());
        state.put(AgentGraphState.EXPERT_ROUND, 0);
        String memory = buildConversationMemory(conversationId);
        if (memory != null) {
            state.put(AgentGraphState.CONVERSATION_MEMORY, memory);
        }
        try {
            return execute(run, virtualTeamConfig(team), GraphInput.args(state), sseEmitter, team, members);
        } catch (Exception e) {
            log.error("Agent 团队运行失败 runId={} team={}", run.getId(), team.getTeamCode(), e);
            return finishRun(run, AiAgentRun.STATUS_FAILED, e.getMessage());
        }
    }

    /**
     * 团队配置映射为虚拟 Agent 配置（复用单 Agent 执行链：graphKey/模型绑定/轮次上限）
     */
    private AiAgentConfig virtualTeamConfig(AiAgentTeam team) {
        AiAgentConfig config = new AiAgentConfig();
        config.setAgentCode(team.getTeamCode());
        config.setAgentName(team.getTeamName());
        config.setGraphKey(SupervisorTeamGraph.GRAPH_KEY);
        config.setSystemPrompt(team.getSupervisorPrompt());
        config.setModelBindings(team.getModelBindings());
        config.setMaxSteps(team.getMaxRounds());
        return config;
    }

    /**
     * 人工确认后恢复执行
     */
    public AiAgentRun resume(AiAgentTodo todo, boolean approved, Consumer<AgentEvents.AgentEvent> sseEmitter) {
        AiAgentRun run = runMapper.selectById(todo.getRunId());
        if (run == null) {
            throw new BusinessException("运行记录不存在");
        }
        AiAgentConfig agentConfig;
        AiAgentTeam team = null;
        List<AiAgentTeamMember> members = null;
        if (run.getTeamId() != null) {
            team = teamMapper.selectById(run.getTeamId());
            if (team == null) {
                throw new BusinessException("Agent 团队不存在");
            }
            members = teamMemberMapper.selectList(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AiAgentTeamMember>()
                            .eq(AiAgentTeamMember::getTeamId, team.getId())
                            .orderByAsc(AiAgentTeamMember::getSortOrder));
            agentConfig = virtualTeamConfig(team);
        } else {
            agentConfig = agentConfigMapper.selectById(run.getAgentId());
            if (agentConfig == null) {
                throw new BusinessException("Agent 配置不存在");
            }
        }
        run.setStatus(AiAgentRun.STATUS_RUNNING);
        runMapper.updateById(run);
        try {
            Map<String, Object> resumeUpdate = new HashMap<>();
            if (team != null) {
                resumeUpdate.put(AgentGraphState.EXPERT_APPROVAL, approved ? "APPROVED" : "REJECTED");
            } else {
                resumeUpdate.put(AgentGraphState.APPROVAL, approved ? "APPROVED" : "REJECTED");
            }
            return execute(run, agentConfig, GraphInput.resume(resumeUpdate), sseEmitter, team, members);
        } catch (Exception e) {
            log.error("Agent 恢复失败 runId={}", run.getId(), e);
            return finishRun(run, AiAgentRun.STATUS_FAILED, e.getMessage());
        }
    }

    // ========== 内部 ==========

    private AiAgentRun execute(AiAgentRun run, AiAgentConfig agentConfig, GraphInput graphInput,
                               Consumer<AgentEvents.AgentEvent> sseEmitter,
                               AiAgentTeam team, List<AiAgentTeamMember> teamMembers) {
        StepRecorder stepRecorder = new StepRecorder(run.getId(),
                run.getUserId() == null ? 0L : run.getUserId(),
                runStepMapper, objectMapper, agentProperties.getStepBatchSize(), usageRecorder);
        // 团队图检查点按间隔节流（中断/终态 flush 兜底）；单 Agent 图逐节点落库
        MysqlCheckpointSaver saver = new MysqlCheckpointSaver(checkpointMapper, run.getId(),
                team != null ? Math.max(1, agentProperties.getCheckpointIntervalTeam()) : 1, false);
        try {
            return doExecute(run, agentConfig, graphInput, sseEmitter, team, teamMembers, stepRecorder, saver);
        } finally {
            // 任何退出路径（成功/中断/异常）保证恢复点与缓冲步骤落库（released 后 no-op）
            RunnableConfig config = RunnableConfig.builder().threadId("run-" + run.getId()).build();
            saver.flush(config);
            stepRecorder.flush();
        }
    }

    private AiAgentRun doExecute(AiAgentRun run, AiAgentConfig agentConfig, GraphInput graphInput,
                                 Consumer<AgentEvents.AgentEvent> sseEmitter,
                                 AiAgentTeam team, List<AiAgentTeamMember> teamMembers,
                                 StepRecorder stepRecorder, MysqlCheckpointSaver saver) {
        Consumer<AgentEvents.AgentEvent> emitter = event -> {
            sseEmitter.accept(event);
            stepRecorder.record(event);
        };

        RunScopedCache runCache = new RunScopedCache(agentConfigMapper, modelConfigMapper);
        AgentGraphContext ctx = new AgentGraphContext(
                agentConfig, resolveNodeModels(agentConfig, runCache), toolRegistry, emitter, new AtomicInteger());
        ctx.setRunCache(runCache);
        ctx.setRunId(run.getId());
        ctx.setUserId(run.getUserId());
        ctx.setSubSaverFactory(threadKey -> new MysqlCheckpointSaver(
                checkpointMapper, run.getId(), 1, true));
        if (team != null) {
            ctx.setTeam(team);
            ctx.setTeamMembers(teamMembers);
        }

        AgentGraphDefinition definition = registry.get(agentConfig.getGraphKey());
        int maxSteps = agentConfig.getMaxSteps() == null ? 8 : agentConfig.getMaxSteps();
        // 外层团队图每轮 = supervisor + expert_pool 两节点；专家子图独立编译（各自 maxSteps*3 上限）
        int recursionLimit;
        if (team != null) {
            recursionLimit = maxSteps * 3 + 10;
        } else {
            recursionLimit = maxSteps * 3;
        }
        CompiledGraph<AgentGraphState> app;
        try {
            // releaseThread(false)：动态中断（框架吞异常正常收流）不触发检查点清理，保证审批恢复点；
            // 成功路径由 reachedEnd 后显式 deleteThread 清理
            app = definition.build(ctx).compile(CompileConfig.builder()
                    .checkpointSaver(saver)
                    .recursionLimit(recursionLimit)
                    .releaseThread(false)
                    .build());
        } catch (org.bsc.langgraph4j.GraphStateException e) {
            throw new BusinessException("编排图构建失败: " + e.getMessage());
        }

        String threadId = "run-" + run.getId();
        RunnableConfig config = RunnableConfig.builder().threadId(threadId).build();

        boolean reachedEnd = false;
        String finalAnswer = null;
        try {
            Iterator<NodeOutput<AgentGraphState>> iterator = app.stream(graphInput, config).iterator();
            while (iterator.hasNext()) {
                NodeOutput<AgentGraphState> out = iterator.next();
                if (out.isEND()) {
                    reachedEnd = true;
                    finalAnswer = out.state().value(AgentGraphState.FINAL_ANSWER)
                            .map(String::valueOf).orElse(null);
                } else {
                    run.setLastNode(out.node());
                }
            }
        } catch (RuntimeException streamError) {
            if (containsInterrupt(streamError)) {
                stepRecorder.flush();
                AiAgentTodo todo = new AiAgentTodo();
                todo.setRunId(run.getId());
                todo.setTitle("Agent 高危操作确认（运行 #" + run.getId() + "）");
                todo.setContent(extractInterruptReason(streamError));
                todo.setStatus(AiAgentTodo.STATUS_PENDING);
                todoMapper.insert(todo);
                runMapper.updateById(run);
                emitter.accept(new AgentEvents.AgentEvent(AgentEvents.Type.APPROVAL_REQUIRED, null,
                        "approval", Map.of("runId", run.getId(), "todoId", todo.getId())));
                return finishRun(run, AiAgentRun.STATUS_WAITING_APPROVAL, null);
            }
            throw streamError;
        }

        if (reachedEnd) {
            runMapper.updateById(run);
            run.setStatus(AiAgentRun.STATUS_SUCCEEDED);
            run.setFinalAnswer(finalAnswer);
            emitter.accept(new AgentEvents.AgentEvent(AgentEvents.Type.FINAL, null, "final",
                    Map.of("runId", run.getId(), "answer", finalAnswer == null ? "" : finalAnswer)));
            // 正常完成：显式清理本线程检查点（替代被 releaseThread(false) 关闭的自动清理）
            try {
                saver.deleteThread(threadId);
            } catch (Exception e) {
                log.warn("清理完成 run 的检查点失败 runId={}: {}", run.getId(), e.getMessage());
            }
            return finishRun(run, AiAgentRun.STATUS_SUCCEEDED, null);
        }

        // 未到 END 且无异常：按动态中断处理（框架可能吞掉中断异常正常收流）
        stepRecorder.flush();
        AiAgentTodo todo = new AiAgentTodo();
        todo.setRunId(run.getId());
        todo.setTitle("Agent 高危操作确认（运行 #" + run.getId() + "）");
        todo.setContent(buildTodoContent(run.getId(), config, saver, stepRecorder));
        todo.setStatus(AiAgentTodo.STATUS_PENDING);
        todoMapper.insert(todo);
        runMapper.updateById(run);
        emitter.accept(new AgentEvents.AgentEvent(AgentEvents.Type.APPROVAL_REQUIRED, null,
                "approval", Map.of("runId", run.getId(), "todoId", todo.getId())));
        return finishRun(run, AiAgentRun.STATUS_WAITING_APPROVAL, null);
    }

    private boolean containsInterrupt(Throwable error) {
        return findInterrupt(error) != null;
    }

    /**
     * 提取中断原因（含待执行的高危工具调用明细）作为待办内容
     */
    private String extractInterruptReason(Throwable error) {
        org.bsc.langgraph4j.GraphInterruptException interrupt = findInterrupt(error);
        String reason = interrupt != null ? interrupt.getMessage() : null;
        return reason == null || reason.isBlank()
                ? "Agent 判定需要执行高危工具，请人工确认。"
                : reason;
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

    private Map<String, Object> initialState(String input, List<String> images) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put(AgentGraphState.INPUT, input);
        state.put(AgentGraphState.IMAGES, images == null ? List.of() : images);
        state.put(AgentGraphState.STEPS, 0);
        state.put(AgentGraphState.TOOL_HISTORY, new java.util.ArrayList<>());
        return state;
    }

    /**
     * 多轮记忆：同会话最近 N 次成功 run 的「问/答」摘要（决策/调度提示词注入，追问不重复执行已完成的查询）。
     * 定时任务等无会话场景（conversationId=null）不注入；查询失败静默跳过（不影响主流程）。
     */
    String buildConversationMemory(Long conversationId) {
        if (conversationId == null) {
            return null;
        }
        try {
            List<AiAgentRun> runs = runMapper.selectList(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AiAgentRun>()
                            .eq(AiAgentRun::getConversationId, conversationId)
                            .eq(AiAgentRun::getStatus, AiAgentRun.STATUS_SUCCEEDED)
                            .isNotNull(AiAgentRun::getFinalAnswer)
                            .orderByDesc(AiAgentRun::getId)
                            .last("LIMIT " + Math.max(1, agentProperties.getPrompt().getMemoryRuns())));
            if (runs.isEmpty()) {
                return null;
            }
            int perAnswerBudget = Math.max(200,
                    agentProperties.getPrompt().getMemoryBudget() / runs.size() - 200);
            StringBuilder sb = new StringBuilder();
            for (int i = runs.size() - 1; i >= 0; i--) { // 时间正序呈现
                AiAgentRun run = runs.get(i);
                sb.append("问：").append(truncate(run.getInputSummary(), 200)).append('\n');
                sb.append("答：").append(truncate(run.getFinalAnswer(), perAnswerBudget)).append("\n\n");
            }
            return sb.toString().trim();
        } catch (Exception e) {
            log.warn("多轮记忆加载失败 conversationId={}: {}", conversationId, e.getMessage());
            return null;
        }
    }

    private String truncate(String text, int limit) {
        if (text == null) {
            return "";
        }
        return text.length() > limit ? text.substring(0, limit) + "…" : text;
    }

    private AiAgentRun finishRun(AiAgentRun run, String status, String errorMsg) {
        run.setStatus(status);
        run.setErrorMsg(errorMsg);
        run.setEndTime(LocalDateTime.now());
        runMapper.updateById(run);
        return run;
    }

    /**
     * 兜底待办内容：依次尝试 checkpoint 状态 → 缓冲/落库的最近决策步骤输出，提取待执行的高危工具调用明细
     */
    private String buildTodoContent(Long runId, RunnableConfig config, MysqlCheckpointSaver saver,
                                    StepRecorder stepRecorder) {
        try {
            var checkpoint = saver.get(config);
            if (checkpoint.isPresent()) {
                String fromState = formatCalls(checkpoint.get().getState().get(AgentGraphState.DECISION));
                if (fromState != null) {
                    return fromState;
                }
            }
        } catch (Exception e) {
            log.warn("从 checkpoint 提取待办明细失败 runId={}: {}", runId, e.getMessage());
        }
        // 缓冲优先（中断路径刚 flush 过则缓冲已空，转 DB）
        String decisionOutput = stepRecorder.latestDecisionOutput();
        String fromBuffer = formatDecisionOutput(decisionOutput);
        if (fromBuffer != null) {
            return fromBuffer;
        }
        // 最可靠来源：中断前最近一次决策步骤的落库输出（含模型原始 JSON，SQL 可见）
        try {
            List<AiAgentRunStep> steps = runStepMapper.selectList(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AiAgentRunStep>()
                            .eq(AiAgentRunStep::getRunId, runId)
                            .in(AiAgentRunStep::getNodeName, "decision", "expert_decide")
                            .orderByDesc(AiAgentRunStep::getId)
                            .last("LIMIT 1"));
            if (!steps.isEmpty()) {
                String modelOutput = objectMapper.readTree(steps.get(0).getStepOutput())
                        .path("output").asText("");
                String fromStep = formatCalls(parseDecisionJson(modelOutput));
                if (fromStep != null) {
                    return fromStep;
                }
                if (!modelOutput.isBlank()) {
                    return "Agent 判定需要执行高危工具，请人工确认。模型决策原文：\n" + modelOutput;
                }
            }
        } catch (Exception e) {
            log.warn("从决策步骤提取待办明细失败 runId={}: {}", runId, e.getMessage());
        }
        return "Agent 判定需要执行高危工具（sql_query 等），请确认是否允许继续执行。";
    }

    /**
     * 从决策对象（{"action":"tool","calls":[...]}）格式化调用明细；非工具决策返回 null
     */
    private String formatCalls(Object decisionObj) {
        if (!(decisionObj instanceof Map<?, ?> decision)) {
            return null;
        }
        if (!(decision.get("calls") instanceof List<?> calls) || calls.isEmpty()) {
            return null;
        }
        // 团队模式下 checkpoint 的 DECISION 可能是 Supervisor 的分派决策（agent/task 结构），
        // 并非待执行的工具调用（tool/args），不作为待办明细（交由决策步骤输出兜底）
        boolean anyToolCall = calls.stream()
                .filter(c -> c instanceof Map<?, ?>)
                .anyMatch(c -> ((Map<?, ?>) c).get("tool") != null);
        if (!anyToolCall) {
            return null;
        }
        StringBuilder sb = new StringBuilder("Agent 判定需要执行高危工具，请人工确认：\n");
        for (Object call : calls) {
            if (call instanceof Map<?, ?> c) {
                sb.append("工具: ").append(c.get("tool"));
                if (c.get("args") != null) {
                    sb.append("\n参数: ").append(c.get("args"));
                }
                sb.append("\n\n");
            }
        }
        return sb.toString().trim();
    }

    /**
     * 解析模型输出的决策 JSON（截取首尾大括号），失败返回 null
     */
    private Object parseDecisionJson(String modelOutput) {
        if (modelOutput == null) {
            return null;
        }
        int start = modelOutput.indexOf('{');
        int end = modelOutput.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return null;
        }
        try {
            return objectMapper.readValue(modelOutput.substring(start, end + 1),
                    objectMapper.getTypeFactory().constructMapType(Map.class, String.class, Object.class));
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 从步骤落库 JSON（{"output":"..."}）提取决策并格式化调用明细，失败返回 null
     */
    private String formatDecisionOutput(String stepOutputJson) {
        if (stepOutputJson == null || stepOutputJson.isBlank()) {
            return null;
        }
        try {
            String modelOutput = objectMapper.readTree(stepOutputJson).path("output").asText("");
            return formatCalls(parseDecisionJson(modelOutput));
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 解析 model_bindings：{"节点":{"modelId":1}|{"modelType":"text"}}（经 run 级缓存取模型）
     */
    private Map<String, AiModelConfig> resolveNodeModels(AiAgentConfig agentConfig, RunScopedCache runCache) {
        Map<String, AiModelConfig> nodeModels = new HashMap<>();
        if (agentConfig.getModelBindings() == null || agentConfig.getModelBindings().isBlank()) {
            return nodeModels;
        }
        try {
            JsonNode bindings = objectMapper.readTree(agentConfig.getModelBindings());
            bindings.fields().forEachRemaining(entry -> {
                String node = entry.getKey();
                JsonNode binding = entry.getValue();
                AiModelConfig model = null;
                if (binding.has("modelId")) {
                    model = runCache.model(binding.get("modelId").asLong());
                } else if (binding.has("modelType")) {
                    model = runCache.modelByType(binding.get("modelType").asText());
                }
                if (model != null) {
                    nodeModels.put(node, model);
                }
            });
        } catch (Exception e) {
            log.warn("解析 model_bindings 失败: {}", e.getMessage());
        }
        return nodeModels;
    }
}
