package com.forge.modules.ai.agent.kernel;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.forge.common.exception.BusinessException;
import com.forge.modules.ai.agent.AgentEvents;
import com.forge.modules.ai.agent.tools.ToolRegistry;
import com.forge.modules.ai.entity.AiAgentConfig;
import com.forge.modules.ai.entity.AiAgentRun;
import com.forge.modules.ai.entity.AiAgentRunStep;
import com.forge.modules.ai.entity.AiAgentTodo;
import com.forge.modules.ai.entity.AiModelConfig;
import com.forge.modules.ai.mapper.AiAgentConfigMapper;
import com.forge.modules.ai.mapper.AiAgentCheckpointMapper;
import com.forge.modules.ai.mapper.AiAgentRunMapper;
import com.forge.modules.ai.mapper.AiAgentRunStepMapper;
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
    private final AiAgentConfigMapper agentConfigMapper;
    private final AiAgentRunMapper runMapper;
    private final AiAgentRunStepMapper runStepMapper;
    private final AiAgentTodoMapper todoMapper;
    private final AiAgentCheckpointMapper checkpointMapper;
    private final AiModelConfigMapper modelConfigMapper;
    private final ObjectMapper objectMapper;

    /**
     * 流式执行：返回最终状态（SUCCEEDED/WAITING_APPROVAL/FAILED）
     */
    public AiAgentRun runStream(AiAgentConfig agentConfig, Long conversationId, Long userId,
                                String input, List<String> images,
                                Consumer<AgentEvents.AgentEvent> sseEmitter) {
        AiAgentRun run = new AiAgentRun();
        run.setAgentId(agentConfig.getId());
        run.setConversationId(conversationId);
        run.setUserId(userId);
        run.setTriggerType("CONVERSATION");
        run.setStatus(AiAgentRun.STATUS_RUNNING);
        run.setInputSummary(input != null && input.length() > 400 ? input.substring(0, 400) : input);
        run.setStartTime(LocalDateTime.now());
        runMapper.insert(run);

        try {
            return execute(run, agentConfig, GraphInput.args(initialState(input, images)), sseEmitter);
        } catch (Exception e) {
            log.error("Agent 运行失败 runId={}", run.getId(), e);
            return finishRun(run, AiAgentRun.STATUS_FAILED, e.getMessage());
        }
    }

    /**
     * 人工确认后恢复执行
     */
    public AiAgentRun resume(AiAgentTodo todo, boolean approved, Consumer<AgentEvents.AgentEvent> sseEmitter) {
        AiAgentRun run = runMapper.selectById(todo.getRunId());
        if (run == null) {
            throw new BusinessException("运行记录不存在");
        }
        AiAgentConfig agentConfig = agentConfigMapper.selectById(run.getAgentId());
        if (agentConfig == null) {
            throw new BusinessException("Agent 配置不存在");
        }
        run.setStatus(AiAgentRun.STATUS_RUNNING);
        runMapper.updateById(run);
        try {
            Map<String, Object> resumeUpdate = new HashMap<>();
            resumeUpdate.put(AgentGraphState.APPROVAL, approved ? "APPROVED" : "REJECTED");
            return execute(run, agentConfig, GraphInput.resume(resumeUpdate), sseEmitter);
        } catch (Exception e) {
            log.error("Agent 恢复失败 runId={}", run.getId(), e);
            return finishRun(run, AiAgentRun.STATUS_FAILED, e.getMessage());
        }
    }

    // ========== 内部 ==========

    private AiAgentRun execute(AiAgentRun run, AiAgentConfig agentConfig, GraphInput graphInput,
                               Consumer<AgentEvents.AgentEvent> sseEmitter) {
        Consumer<AgentEvents.AgentEvent> emitter = event -> {
            sseEmitter.accept(event);
            persistStep(run.getId(), event);
        };

        AgentGraphContext ctx = new AgentGraphContext(
                agentConfig, resolveNodeModels(agentConfig), toolRegistry, emitter, new AtomicInteger());

        AgentGraphDefinition definition = registry.get(agentConfig.getGraphKey());
        int maxSteps = agentConfig.getMaxSteps() == null ? 8 : agentConfig.getMaxSteps();
        MysqlCheckpointSaver saver = new MysqlCheckpointSaver(checkpointMapper, objectMapper, run.getId());
        CompiledGraph<AgentGraphState> app;
        try {
            app = definition.build(ctx).compile(CompileConfig.builder()
                    .checkpointSaver(saver)
                    .recursionLimit(maxSteps * 3)
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
                AiAgentTodo todo = new AiAgentTodo();
                todo.setRunId(run.getId());
                todo.setTitle("Agent 高危操作确认（运行 #" + run.getId() + "）");
                todo.setContent(buildTodoContent(ctx));
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
            emitter.accept(new AgentEvents.AgentEvent(AgentEvents.Type.FINAL, null, "final",
                    Map.of("runId", run.getId(), "answer", finalAnswer == null ? "" : finalAnswer)));
            return finishRun(run, AiAgentRun.STATUS_SUCCEEDED, null);
        }

        // 未到 END 且无异常：按动态中断处理（框架可能吞掉中断异常正常收流）
        AiAgentTodo todo = new AiAgentTodo();
        todo.setRunId(run.getId());
        todo.setTitle("Agent 高危操作确认（运行 #" + run.getId() + "）");
        todo.setContent(buildTodoContent(ctx));
        todo.setStatus(AiAgentTodo.STATUS_PENDING);
        todoMapper.insert(todo);
        runMapper.updateById(run);
        emitter.accept(new AgentEvents.AgentEvent(AgentEvents.Type.APPROVAL_REQUIRED, null,
                "approval", Map.of("runId", run.getId(), "todoId", todo.getId())));
        return finishRun(run, AiAgentRun.STATUS_WAITING_APPROVAL, null);
    }

    private boolean containsInterrupt(Throwable error) {
        Throwable cur = error;
        int depth = 0;
        while (cur != null && depth++ < 10) {
            if (cur instanceof org.bsc.langgraph4j.GraphInterruptException) {
                return true;
            }
            cur = cur.getCause();
        }
        return false;
    }

    private Map<String, Object> initialState(String input, List<String> images) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put(AgentGraphState.INPUT, input);
        state.put(AgentGraphState.IMAGES, images == null ? List.of() : images);
        state.put(AgentGraphState.STEPS, 0);
        state.put(AgentGraphState.TOOL_HISTORY, new java.util.ArrayList<>());
        return state;
    }

    private AiAgentRun finishRun(AiAgentRun run, String status, String errorMsg) {
        run.setStatus(status);
        run.setErrorMsg(errorMsg);
        run.setEndTime(LocalDateTime.now());
        runMapper.updateById(run);
        return run;
    }

    private String buildTodoContent(AgentGraphContext ctx) {
        return "Agent 判定需要执行高危工具（sql_query 等），请确认是否允许继续执行。";
    }

    /**
     * 解析 model_bindings：{"节点":{"modelId":1}|{"modelType":"text"}}
     */
    private Map<String, AiModelConfig> resolveNodeModels(AiAgentConfig agentConfig) {
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
                    model = modelConfigMapper.selectById(binding.get("modelId").asLong());
                } else if (binding.has("modelType")) {
                    String modelType = binding.get("modelType").asText();
                    model = modelConfigMapper.selectList(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AiModelConfig>()
                                    .eq(AiModelConfig::getModelType, modelType)
                                    .eq(AiModelConfig::getStatus, 1)
                                    .orderByAsc(AiModelConfig::getId)
                                    .last("LIMIT 1"))
                            .stream().findFirst().orElse(null);
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

    private void persistStep(long runId, AgentEvents.AgentEvent event) {
        if (event.type() != AgentEvents.Type.STEP_END && event.type() != AgentEvents.Type.TOOL_RESULT) {
            return;
        }
        try {
            AiAgentRunStep step = new AiAgentRunStep();
            step.setRunId(runId);
            step.setSeq(event.payload() != null && event.payload().get("seq") instanceof Number n
                    ? n.intValue() : 0);
            step.setNodeName(event.node() == null ? event.name() : event.node());
            step.setStepType(event.type() == AgentEvents.Type.TOOL_RESULT ? "TOOL" : nodeStepType(event.node()));
            step.setToolName(event.type() == AgentEvents.Type.TOOL_RESULT ? event.name() : null);
            if (event.payload() != null) {
                if (event.payload().get("modelId") instanceof Number m) {
                    step.setModelId(m.longValue());
                }
                step.setStepOutput(toJson(event.payload()));
                if (event.payload().get("durationMs") instanceof Number d) {
                    step.setDurationMs(d.intValue());
                }
                if (event.payload().get("tokensIn") instanceof Number ti) {
                    step.setTokensIn(ti.intValue());
                }
                if (event.payload().get("tokensOut") instanceof Number to) {
                    step.setTokensOut(to.intValue());
                }
                step.setStatus("FAILED".equals(event.payload().get("status")) ? "FAILED" : "SUCCESS");
            }
            runStepMapper.insert(step);
        } catch (Exception e) {
            log.warn("Agent 步骤落库失败 runId={}: {}", runId, e.getMessage());
        }
    }

    private String nodeStepType(String node) {
        return switch (node == null ? "" : node) {
            case "multimodal" -> "MULTIMODAL";
            case "approval" -> "INTERRUPT";
            default -> "LLM";
        };
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return String.valueOf(value);
        }
    }
}
