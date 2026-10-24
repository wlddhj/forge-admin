package com.forge.modules.ai.agent.kernel;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.forge.modules.ai.agent.AgentEvents;
import com.forge.modules.ai.entity.AiAgentRunStep;
import com.forge.modules.ai.mapper.AiAgentRunStepMapper;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 步骤事件批量落库器：事件先入内存缓冲（不阻塞 SSE 发射线程），达到批量阈值或
 * run 终态/中断待办生成前统一落库；单条失败降级重试一次，仍失败仅告警不阻断流程。
 */
@Slf4j
public class StepRecorder {

    private final long runId;
    private final AiAgentRunStepMapper runStepMapper;
    private final ObjectMapper objectMapper;
    private final int batchSize;

    private final List<AiAgentRunStep> buffer = Collections.synchronizedList(new ArrayList<>());

    public StepRecorder(long runId, AiAgentRunStepMapper runStepMapper,
                        ObjectMapper objectMapper, int batchSize) {
        this.runId = runId;
        this.runStepMapper = runStepMapper;
        this.objectMapper = objectMapper;
        this.batchSize = Math.max(1, batchSize);
    }

    /**
     * 记录事件（仅 STEP_END/TOOL_RESULT）；缓冲满时触发落库。任何异常不外抛。
     */
    public void record(AgentEvents.AgentEvent event) {
        if (event.type() != AgentEvents.Type.STEP_END && event.type() != AgentEvents.Type.TOOL_RESULT) {
            return;
        }
        try {
            buffer.add(toStep(event));
            if (buffer.size() >= batchSize) {
                flush();
            }
        } catch (Exception e) {
            log.warn("Agent 步骤入缓冲失败 runId={}: {}", runId, e.getMessage());
        }
    }

    /**
     * 落库全部缓冲步骤（批量点：阈值触发/run 终态/待办生成前）
     */
    public void flush() {
        List<AiAgentRunStep> pending;
        synchronized (buffer) {
            if (buffer.isEmpty()) {
                return;
            }
            pending = new ArrayList<>(buffer);
            buffer.clear();
        }
        for (AiAgentRunStep step : pending) {
            if (!insertWithRetry(step)) {
                log.warn("Agent 步骤落库失败(已重试) runId={} node={}", runId, step.getNodeName());
            }
        }
    }

    /** 缓冲中的最新决策步骤输出（待办明细兜底数据源，避免依赖已 flush 的 DB 记录） */
    public String latestDecisionOutput() {
        synchronized (buffer) {
            for (int i = buffer.size() - 1; i >= 0; i--) {
                AiAgentRunStep step = buffer.get(i);
                if ("decision".equals(step.getNodeName()) || "expert_decide".equals(step.getNodeName())) {
                    return step.getStepOutput();
                }
            }
        }
        return null;
    }

    private boolean insertWithRetry(AiAgentRunStep step) {
        if (runStepMapper.insert(step) > 0) {
            return true;
        }
        return runStepMapper.insert(step) > 0;
    }

    private AiAgentRunStep toStep(AgentEvents.AgentEvent event) {
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
        return step;
    }

    private String nodeStepType(String node) {
        return switch (node == null ? "" : node) {
            case "multimodal" -> "MULTIMODAL";
            case "approval", "expert_approval" -> "INTERRUPT";
            case "supervisor" -> "SUPERVISOR";
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
