package com.forge.modules.ai.agent.graph;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.forge.modules.ai.agent.AgentEvents;
import com.forge.modules.ai.agent.AgentProperties;
import com.forge.modules.ai.agent.kernel.AgentGraphRegistry;
import com.forge.modules.ai.agent.kernel.AgentGraphRunner;
import com.forge.modules.ai.agent.tools.AgentTool;
import com.forge.modules.ai.agent.tools.ToolRegistry;
import com.forge.modules.ai.client.LlmClient;
import com.forge.modules.ai.client.NativeToolCalling;
import com.forge.modules.ai.dto.request.ChatRequest;
import com.forge.modules.ai.dto.response.ChatResponse;
import com.forge.modules.ai.entity.AiAgentCheckpoint;
import com.forge.modules.ai.entity.AiAgentConfig;
import com.forge.modules.ai.entity.AiAgentRun;
import com.forge.modules.ai.entity.AiAgentTeam;
import com.forge.modules.ai.entity.AiAgentTeamMember;
import com.forge.modules.ai.entity.AiAgentTodo;
import com.forge.modules.ai.entity.AiModelConfig;
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
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 团队图并行分派单测：批量分派并行执行（latch 验证并发）、汇报合并、
 * 高危中断聚合 + 恢复保真（已完成专家凭 EXPERT_DONE 标记跳过，被中断专家续跑）（AC5/AC6）
 */
@ExtendWith(MockitoExtension.class)
class SupervisorTeamParallelTest {

    @Mock
    private LlmClient llmClient;
    @Mock
    private NativeToolCalling nativeToolCalling;
    @Mock
    private AiAgentRunMapper runMapper;
    @Mock
    private AiAgentTodoMapper todoMapper;
    @Mock
    private com.forge.modules.ai.agent.tools.TableSchemaInspector tableSchemaInspector;
    @Mock
    private AiAgentConfigMapper agentConfigMapper;
    @Mock
    private AiModelConfigMapper modelConfigMapper;
    @Mock
    private AiAgentTeamMapper teamMapper;
    @Mock
    private AiAgentTeamMemberMapper teamMemberMapper;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AgentProperties agentProperties = new AgentProperties();
    private final ExecutorService agentExecutor = Executors.newFixedThreadPool(6);
    private AiAgentCheckpointMapper checkpointMapper;
    private ToolRegistry toolRegistry;
    private final AtomicInteger echoExecutions = new AtomicInteger();
    private final AtomicInteger riskExecutions = new AtomicInteger();
    private final Map<String, AtomicInteger> expertDecides = new ConcurrentHashMap<>();
    private final List<AiAgentTodo> todos = new CopyOnWriteArrayList<>();

    private AgentGraphRunner runner;
    private AiAgentTeam team;
    private List<AiAgentTeamMember> members;

    @BeforeEach
    void setUp() {
        // 单测环境注册 MyBatis Plus TableInfo（LambdaQueryWrapper 取参解析依赖）
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                new org.apache.ibatis.builder.MapperBuilderAssistant(
                        new com.baomidou.mybatisplus.core.MybatisConfiguration(), ""),
                AiAgentCheckpoint.class);
        checkpointMapper = inMemoryCheckpointMapper();
        toolRegistry = new ToolRegistry(List.of(new EchoTool(), new RiskTool()));
        expertDecides.put("A", new AtomicInteger());
        expertDecides.put("B", new AtomicInteger());

        PromptBudgeter promptBudgeter = new PromptBudgeter(agentProperties);
        DecisionJsonParser parser = new DecisionJsonParser(objectMapper);
        MultimodalSupport multimodalSupport = new MultimodalSupport(llmClient, parser);
        SupervisorTeamGraph teamGraph = new SupervisorTeamGraph(llmClient, objectMapper, parser,
                multimodalSupport, tableSchemaInspector, promptBudgeter, nativeToolCalling,
                agentProperties, agentExecutor);
        AgentGraphRegistry registry = new AgentGraphRegistry(List.of(teamGraph));

        runner = new AgentGraphRunner(registry, toolRegistry, agentProperties,
                agentConfigMapper, runMapper, mock(AiAgentRunStepMapper.class),
                todoMapper, checkpointMapper, modelConfigMapper,
                teamMapper, teamMemberMapper, objectMapper);

        // 无记忆、无表白名单
        lenient().when(runMapper.selectList(any())).thenReturn(List.of());
        lenient().when(tableSchemaInspector.enabledTables()).thenReturn(List.of());
        // 专家 Agent 配置 + 决策模型（runCache 经此取数）
        lenient().when(agentConfigMapper.selectById(21L)).thenReturn(agent(21L, "expertA", "专家A"));
        lenient().when(agentConfigMapper.selectById(22L)).thenReturn(agent(22L, "expertB", "专家B"));
        AiModelConfig decisionModel = new AiModelConfig();
        decisionModel.setId(10L);
        decisionModel.setModelName("决策模型");
        decisionModel.setModelCode("glm-4.6");
        decisionModel.setStatus(1);
        // supervisor 绑定 modelId=1，专家决策绑定 modelId=10
        lenient().when(modelConfigMapper.selectById(1L)).thenReturn(decisionModel);
        lenient().when(modelConfigMapper.selectById(10L)).thenReturn(decisionModel);
        lenient().when(teamMapper.selectById(5L)).thenAnswer(inv -> team);
        lenient().when(teamMemberMapper.selectList(any())).thenAnswer(inv -> members);
        doAnswer(inv -> {
            AiAgentRun run = inv.getArgument(0);
            run.setId(1L);
            return 1;
        }).when(runMapper).insert(any(AiAgentRun.class));
        lenient().doAnswer(inv -> {
            AiAgentTodo todo = inv.getArgument(0);
            todo.setId(100L);
            todo.setRunId(1L);
            todos.add(todo);
            return 1;
        }).when(todoMapper).insert(any(AiAgentTodo.class));

        team = new AiAgentTeam();
        team.setId(5L);
        team.setTeamCode("team-x");
        team.setTeamName("团队X");
        team.setSupervisorPrompt("你是调度官");
        team.setModelBindings("{\"supervisor\":{\"modelId\":1}}");
        team.setMaxRounds(3);

        members = List.of(member(11L, 21L, "数据查询"), member(12L, 22L, "报表统计"));
    }

    private AiAgentConfig agent(Long id, String code, String prompt) {
        AiAgentConfig config = new AiAgentConfig();
        config.setId(id);
        config.setAgentCode(code);
        config.setAgentName(code);
        config.setStatus(1);
        config.setSystemPrompt("你是" + code + "，" + prompt);
        config.setModelBindings("{\"decision\":{\"modelId\":10}}");
        config.setMaxSteps(4);
        return config;
    }

    // ========== 场景 1：批量分派并行执行 + 汇报合并 ==========

    @Test
    void 批量分派并行执行并合并汇报() {
        CountDownLatch bothInFlight = new CountDownLatch(2);
        stubLlm(bothInFlight, false);

        AiAgentRun run = runner.runTeamStream(team, null, 1L, "CONVERSATION", "分析数据", null, event -> {
        });

        assertEquals(AiAgentRun.STATUS_SUCCEEDED, run.getStatus());
        assertEquals("团队最终回答", run.getFinalAnswer());
        assertEquals(1, echoExecutions.get());
        // 并发验证：两个专家的决策必须同时 in-flight（串行会 latch 超时导致失败）
        assertTrue(bothInFlight.getCount() == 0, "两个专家未并行执行");
        assertTrue(todos.isEmpty(), "不应产生审批待办");
    }

    // ========== 场景 2：高危中断聚合 + 恢复保真 ==========

    @Test
    void 高危中断生成待办_恢复时已完成专家不重跑() {
        CountDownLatch bothInFlight = new CountDownLatch(2);
        stubLlm(bothInFlight, true);

        AiAgentRun run = runner.runTeamStream(team, null, 1L, "CONVERSATION", "高危分析", null, event -> {
        });

        assertEquals(AiAgentRun.STATUS_WAITING_APPROVAL, run.getStatus());
        assertEquals(1, todos.size());
        assertTrue(todos.get(0).getContent().contains("高危"), "待办应包含高危详情");
        int bDecidesBefore = expertDecides.get("B").get();
        int aDecidesBefore = expertDecides.get("A").get();

        // 恢复：批准
        when(runMapper.selectById(1L)).thenReturn(run);
        AiAgentRun resumed = runner.resume(todos.get(0), true, event -> {
        });

        assertEquals(AiAgentRun.STATUS_SUCCEEDED, resumed.getStatus());
        assertEquals("团队最终回答", resumed.getFinalAnswer());
        // 恢复后高危工具已执行
        assertEquals(1, riskExecutions.get());
        // 专家 A 从检查点续跑：仅补 1 次决策（final）；专家 B 凭 EXPERT_DONE 标记跳过（决策次数不变）
        assertEquals(aDecidesBefore + 1, expertDecides.get("A").get());
        assertEquals(bDecidesBefore, expertDecides.get("B").get());
    }

    // ========== LLM 桩 ==========

    /**
     * 决策桩：supervisor 第 1 次批量分派两专家、第 2 次 final；
     * 专家 A：先工具（scenario 高危时用 risk_tool）后 final；专家 B：直接 final。
     * 专家决策进入时在 latch 上计数并等待并发对齐（验证并行度）。
     */
    private void stubLlm(CountDownLatch bothInFlight, boolean highRisk) {
        AtomicInteger supervisorCalls = new AtomicInteger();
        lenient().when(llmClient.chatForDecision(any(), any(), any())).thenAnswer(inv -> {
            ChatRequest request = inv.getArgument(0);
            String content = request.getContent() == null ? "" : request.getContent();
            if (content.contains("团队专家清单")) {
                String decision = supervisorCalls.incrementAndGet() == 1
                        ? "{\"action\":\"dispatch\",\"calls\":[{\"agent\":\"expertA\",\"task\":\"任务A\"},"
                          + "{\"agent\":\"expertB\",\"task\":\"任务B\"}],\"thought\":\"并行\"}"
                        : "{\"action\":\"final\",\"answer\":\"团队最终回答\"}";
                return success(decision);
            }
            if (content.contains("你是expertA")) {
                bothInFlight.countDown();
                awaitLatch(bothInFlight);
                int call = expertDecides.get("A").incrementAndGet();
                String decision = call == 1
                        ? (highRisk
                                ? "{\"action\":\"tool\",\"calls\":[{\"tool\":\"risk_tool\",\"args\":{}}]}"
                                : "{\"action\":\"tool\",\"calls\":[{\"tool\":\"echo_tool\",\"args\":{\"text\":\"a\"}}]}")
                        : "{\"action\":\"final\",\"answer\":\"专家A结论\"}";
                return success(decision);
            }
            if (content.contains("你是expertB")) {
                bothInFlight.countDown();
                awaitLatch(bothInFlight);
                expertDecides.get("B").incrementAndGet();
                return success("{\"action\":\"final\",\"answer\":\"专家B结论\"}");
            }
            return success("{\"action\":\"final\",\"answer\":\"unknown\"}");
        });
    }

    private void awaitLatch(CountDownLatch latch) {
        try {
            assertTrue(latch.await(10, TimeUnit.SECONDS), "专家未并行执行（latch 超时）");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private ChatResponse success(String content) {
        ChatResponse response = new ChatResponse();
        response.setSuccess(true);
        response.setContent(content);
        response.setModelName("m");
        response.setInputTokens(1);
        response.setOutputTokens(1);
        return response;
    }

    private AiAgentTeamMember member(Long id, Long agentId, String role) {
        AiAgentTeamMember member = new AiAgentTeamMember();
        member.setId(id);
        member.setTeamId(5L);
        member.setAgentId(agentId);
        member.setMemberRole(role);
        member.setAllowedTools("[\"echo_tool\",\"risk_tool\"]");
        member.setMaxSteps(4);
        member.setSortOrder(id.intValue());
        return member;
    }

    // ========== 测试工具 ==========

    class EchoTool implements AgentTool {
        @Override
        public String name() {
            return "echo_tool";
        }

        @Override
        public String description() {
            return "回显";
        }

        @Override
        public Map<String, String> parameterSpec() {
            return Map.of("text", "文本");
        }

        @Override
        public Object execute(Map<String, Object> args) {
            echoExecutions.incrementAndGet();
            return Map.of("echo", args.get("text"));
        }
    }

    class RiskTool implements AgentTool {
        @Override
        public String name() {
            return "risk_tool";
        }

        @Override
        public String description() {
            return "高危工具";
        }

        @Override
        public Map<String, String> parameterSpec() {
            return Map.of();
        }

        @Override
        public boolean highRisk() {
            return true;
        }

        @Override
        public Object execute(Map<String, Object> args) {
            riskExecutions.incrementAndGet();
            return Map.of("ok", true);
        }
    }

    /**
     * 内存版 checkpoint mapper（mock + 有状态 Answer）：threadId 过滤复刻查询语义
     * （list 排除 expert-done 标记、selectOne 取标记、count 判存在、delete 清线程），支撑中断-恢复全链路单测
     */
    @SuppressWarnings("unchecked")
    private AiAgentCheckpointMapper inMemoryCheckpointMapper() {
        Map<String, List<AiAgentCheckpoint>> table = new ConcurrentHashMap<>();
        AtomicLong ids = new AtomicLong();
        java.util.function.Consumer<AiAgentCheckpoint> store = entity -> {
            if (entity.getId() == null) {
                entity.setId(ids.incrementAndGet());
            }
            List<AiAgentCheckpoint> rows = table.computeIfAbsent(entity.getThreadId(), k -> new ArrayList<>());
            synchronized (rows) {
                rows.removeIf(r -> entity.getCheckpointId().equals(r.getCheckpointId()));
                rows.add(entity);
            }
        };
        AiAgentCheckpointMapper mapper = mock(AiAgentCheckpointMapper.class);
        lenient().when(mapper.upsert(any(AiAgentCheckpoint.class))).thenAnswer(inv -> {
            store.accept(inv.getArgument(0));
            return 1;
        });
        lenient().when(mapper.insert(any(AiAgentCheckpoint.class))).thenAnswer(inv -> {
            store.accept(inv.getArgument(0));
            return 1;
        });
        lenient().when(mapper.selectList(any(Wrapper.class))).thenAnswer(inv -> {
            Wrapper<AiAgentCheckpoint> wrapper = inv.getArgument(0);
            String threadId = threadIdOf(wrapper);
            boolean excludeDone = paramValues(wrapper).contains("expert-done");
            List<AiAgentCheckpoint> rows = new ArrayList<>(table.getOrDefault(threadId, List.of()));
            if (excludeDone) {
                rows.removeIf(r -> "expert-done".equals(r.getCheckpointId()));
            }
            rows.sort(Comparator.comparing(AiAgentCheckpoint::getId).reversed()); // 框架取队头=最新
            return rows;
        });
        lenient().when(mapper.selectOne(any(Wrapper.class))).thenAnswer(inv -> {
            Wrapper<AiAgentCheckpoint> wrapper = inv.getArgument(0);
            return table.getOrDefault(threadIdOf(wrapper), List.of()).stream()
                    .filter(r -> "expert-done".equals(r.getCheckpointId()))
                    .findFirst().orElse(null);
        });
        lenient().when(mapper.selectCount(any(Wrapper.class))).thenAnswer(inv ->
                (long) table.getOrDefault(threadIdOf(inv.getArgument(0)), List.of()).size());
        lenient().when(mapper.delete(any(Wrapper.class))).thenAnswer(inv -> {
            table.remove(threadIdOf(inv.getArgument(0)));
            return 1;
        });
        return mapper;
    }

    private String threadIdOf(Wrapper<AiAgentCheckpoint> wrapper) {
        return paramValues(wrapper).stream()
                .filter(v -> v instanceof String s && s.startsWith("run-"))
                .map(Object::toString).findFirst().orElse("");
    }

    private List<Object> paramValues(Wrapper<AiAgentCheckpoint> wrapper) {
        if (wrapper instanceof com.baomidou.mybatisplus.core.conditions.AbstractWrapper<?, ?, ?> aw) {
            // MP 3.5.17：条件参数在 SQL 片段渲染后才物化
            aw.getSqlSegment();
            return new ArrayList<>(aw.getParamNameValuePairs().values());
        }
        return List.of();
    }
}
