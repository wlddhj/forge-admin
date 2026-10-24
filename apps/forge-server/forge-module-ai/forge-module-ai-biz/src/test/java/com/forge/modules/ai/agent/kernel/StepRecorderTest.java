package com.forge.modules.ai.agent.kernel;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.forge.modules.ai.agent.AgentEvents;
import com.forge.modules.ai.entity.AiAgentRunStep;
import com.forge.modules.ai.mapper.AiAgentRunStepMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 步骤批量落库器单测：批量阈值触发、终态 flush 完整、非落库事件忽略、缓冲决策兜底读取（AC8）
 */
class StepRecorderTest {

    private AiAgentRunStepMapper mapper;
    private ObjectMapper objectMapper;
    private StepRecorder recorder;

    @BeforeEach
    void setUp() {
        mapper = mock(AiAgentRunStepMapper.class);
        objectMapper = new ObjectMapper();
        when(mapper.insert(any(AiAgentRunStep.class))).thenReturn(1);
    }

    @Test
    void 达到批量阈值自动落库() {
        recorder = new StepRecorder(1L, mapper, objectMapper, 2);
        recorder.record(stepEnd(1, "decision"));
        verify(mapper, never()).insert(any(AiAgentRunStep.class));
        recorder.record(stepEnd(2, "tools"));
        verify(mapper, times(2)).insert(any(AiAgentRunStep.class));
    }

    @Test
    void 终态flush保证缓冲全部落库() {
        recorder = new StepRecorder(1L, mapper, objectMapper, 100);
        recorder.record(stepEnd(1, "decision"));
        recorder.record(toolResult(2, "sql_query"));
        recorder.record(stepEnd(3, "tools"));
        verify(mapper, never()).insert(any(AiAgentRunStep.class));

        recorder.flush();
        ArgumentCaptor<AiAgentRunStep> captor = ArgumentCaptor.forClass(AiAgentRunStep.class);
        verify(mapper, times(3)).insert(captor.capture());
        List<AiAgentRunStep> inserted = captor.getAllValues();
        assertEquals("decision", inserted.get(0).getNodeName());
        assertEquals("TOOL", inserted.get(1).getStepType());
        assertEquals("sql_query", inserted.get(1).getToolName());
        assertEquals("LLM", inserted.get(2).getStepType());
    }

    @Test
    void 非落库事件类型忽略() {
        recorder = new StepRecorder(1L, mapper, objectMapper, 1);
        recorder.record(new AgentEvents.AgentEvent(AgentEvents.Type.STEP_START, "decision", "工具决策", Map.of()));
        recorder.record(new AgentEvents.AgentEvent(AgentEvents.Type.CONTENT, "summary", "总结", Map.of()));
        recorder.record(new AgentEvents.AgentEvent(AgentEvents.Type.THOUGHT, "decision", "思考", Map.of()));
        recorder.flush();
        verify(mapper, never()).insert(any(AiAgentRunStep.class));
    }

    @Test
    void 落库失败重试一次后告警不抛出() {
        recorder = new StepRecorder(1L, mapper, objectMapper, 100);
        when(mapper.insert(any(AiAgentRunStep.class))).thenReturn(0);
        recorder.record(stepEnd(1, "decision"));
        recorder.flush();
        verify(mapper, times(2)).insert(any(AiAgentRunStep.class));
    }

    @Test
    void 缓冲中读取最近决策步骤输出() {
        recorder = new StepRecorder(1L, mapper, objectMapper, 100);
        recorder.record(stepEnd(1, "tools"));
        recorder.record(new AgentEvents.AgentEvent(AgentEvents.Type.STEP_END, "decision", "工具决策",
                Map.of("seq", 9, "status", "SUCCESS", "output", "{\"action\":\"tool\"}")));
        String output = recorder.latestDecisionOutput();
        assertTrue(output.contains("action"));
        recorder.flush();
        assertNull(recorder.latestDecisionOutput());
    }

    // ========== 工具 ==========

    private AgentEvents.AgentEvent stepEnd(int seq, String node) {
        return new AgentEvents.AgentEvent(AgentEvents.Type.STEP_END, node, node,
                Map.of("seq", seq, "status", "SUCCESS", "output", "ok"));
    }

    private AgentEvents.AgentEvent toolResult(int seq, String tool) {
        return new AgentEvents.AgentEvent(AgentEvents.Type.TOOL_RESULT, "tools", tool,
                Map.of("seq", seq, "status", "SUCCESS", "output", "rows"));
    }
}
