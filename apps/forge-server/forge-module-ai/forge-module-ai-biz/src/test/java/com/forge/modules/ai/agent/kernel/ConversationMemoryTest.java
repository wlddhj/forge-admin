package com.forge.modules.ai.agent.kernel;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.forge.modules.ai.agent.AgentProperties;
import com.forge.modules.ai.agent.tools.ToolRegistry;
import com.forge.modules.ai.entity.AiAgentRun;
import com.forge.modules.ai.mapper.AiAgentCheckpointMapper;
import com.forge.modules.ai.mapper.AiAgentConfigMapper;
import com.forge.modules.ai.mapper.AiAgentRunMapper;
import com.forge.modules.ai.mapper.AiAgentRunStepMapper;
import com.forge.modules.ai.mapper.AiAgentTeamMapper;
import com.forge.modules.ai.mapper.AiAgentTeamMemberMapper;
import com.forge.modules.ai.mapper.AiAgentTodoMapper;
import com.forge.modules.ai.mapper.AiModelConfigMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 多轮记忆单测：近 N 次成功 run 的问答摘要构建、预算截断、空会话/无成功 run 返回 null（AC4）
 */
class ConversationMemoryTest {

    private AgentGraphRegistry registry;
    private ToolRegistry toolRegistry;
    private AgentProperties agentProperties;
    private AiAgentRunMapper runMapper;
    private AgentGraphRunner runner;

    @BeforeEach
    void setUp() {
        registry = mock(AgentGraphRegistry.class);
        toolRegistry = mock(ToolRegistry.class);
        agentProperties = new AgentProperties();
        runMapper = mock(AiAgentRunMapper.class);
        runner = new AgentGraphRunner(registry, toolRegistry, agentProperties,
                mock(AiAgentConfigMapper.class), runMapper, mock(AiAgentRunStepMapper.class),
                mock(AiAgentTodoMapper.class), mock(AiAgentCheckpointMapper.class),
                mock(AiModelConfigMapper.class), mock(AiAgentTeamMapper.class),
                mock(AiAgentTeamMemberMapper.class), new ObjectMapper());
    }

    @Test
    void 无会话返回null() {
        assertNull(runner.buildConversationMemory(null));
    }

    @Test
    void 无成功run返回null() {
        when(runMapper.selectList(any())).thenReturn(List.of());
        assertNull(runner.buildConversationMemory(9L));
    }

    @Test
    void 构建问答摘要_时间正序_受预算约束() {
        AiAgentRun older = run("上月的销售总数是多少", "上 月销售 1000 件");
        AiAgentRun newer = run("本月库存余量", "本月 库存 500 件");
        // orderByDesc 查询结果按时间倒序返回
        when(runMapper.selectList(any())).thenReturn(List.of(newer, older));

        String memory = runner.buildConversationMemory(9L);

        assertTrue(memory.contains("销售 1000 件"));
        assertTrue(memory.contains("库存 500 件"));
        // 时间正序：旧问答在前
        assertTrue(memory.indexOf("销售 1000") < memory.indexOf("库存 500"));
    }

    @Test
    void 超长答案被截断() {
        agentProperties.getPrompt().setMemoryBudget(600);
        AiAgentRun run = run("问题", "答".repeat(2000));
        when(runMapper.selectList(any())).thenReturn(List.of(run));

        String memory = runner.buildConversationMemory(9L);

        assertTrue(memory.length() < 1200);
        assertTrue(memory.contains("…"));
    }

    private AiAgentRun run(String input, String answer) {
        AiAgentRun run = new AiAgentRun();
        run.setInputSummary(input);
        run.setFinalAnswer(answer);
        run.setStatus(AiAgentRun.STATUS_SUCCEEDED);
        return run;
    }
}
