package com.forge.modules.ai.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.forge.common.exception.BusinessException;
import com.forge.framework.security.utils.SecurityHelper;
import com.forge.framework.web.websocket.NotificationService;
import com.forge.modules.ai.agent.kernel.AgentGraphRunner;
import com.forge.modules.ai.agent.kernel.AgentRunEventBridge;
import com.forge.modules.ai.entity.AiAgentRun;
import com.forge.modules.ai.entity.AiAgentTodo;
import com.forge.modules.ai.mapper.AiAgentRunMapper;
import com.forge.modules.ai.mapper.AiAgentRunStepMapper;
import com.forge.modules.ai.mapper.AiAgentTeamMapper;
import com.forge.modules.ai.mapper.AiAgentTodoMapper;
import com.forge.modules.ai.service.impl.AiAgentRunServiceImpl;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Agent 待办审批 CAS 抢占单元测试：并发双审批仅一个恢复图执行，
 * 输家条件更新 0 行命中即被拒，不再双倍触发 LLM/工具调用。
 */
@ExtendWith(MockitoExtension.class)
class AiAgentTodoHandleCasTest {

    private static final Long TODO_ID = 20L;
    private static final Long RUN_ID = 60L;
    private static final Long USER_ID = 2L;

    @Mock
    private AiAgentRunMapper runMapper;
    @Mock
    private AiAgentRunStepMapper runStepMapper;
    @Mock
    private AiAgentTodoMapper todoMapper;
    @Mock
    private AiAgentTeamMapper teamMapper;
    @Mock
    private AgentGraphRunner graphRunner;
    @Mock
    private NotificationService notificationService;
    @Mock
    private AiChatService aiChatService;

    private AiAgentRunServiceImpl service;
    private MockedStatic<SecurityHelper> securityHelper;

    @BeforeAll
    static void initTableInfo() {
        // MP 3.5.18：纯单测下 LambdaUpdateWrapper 渲染（getSqlSegment）需要实体 TableInfo 缓存
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, AiAgentTodo.class);
    }

    @BeforeEach
    void setUp() {
        // 真实事件桥（推送走 mock NotificationService），恢复执行线程内 WebSocket 推送为空操作
        AgentRunEventBridge eventBridge = new AgentRunEventBridge(notificationService, new ObjectMapper());
        service = new AiAgentRunServiceImpl(runMapper, runStepMapper, todoMapper, teamMapper,
                graphRunner, notificationService, aiChatService, eventBridge);
        securityHelper = mockStatic(SecurityHelper.class);
    }

    @AfterEach
    void tearDown() {
        securityHelper.close();
    }

    /** 通过前置校验、到达 CAS 抢占段的用例统一装配当前用户静态桩 */
    private void stubApprover() {
        securityHelper.when(SecurityHelper::getCurrentUserId).thenReturn(USER_ID);
        securityHelper.when(SecurityHelper::getCurrentUsername).thenReturn("审批人乙");
    }

    private AiAgentTodo pendingTodo() {
        AiAgentTodo todo = new AiAgentTodo();
        todo.setId(TODO_ID);
        todo.setRunId(RUN_ID);
        todo.setTitle("删除生产数据确认");
        todo.setStatus(AiAgentTodo.STATUS_PENDING);
        return todo;
    }

    @Test
    @DisplayName("抢占成功：条件更新 WHERE id+status=PENDING，胜者置结论并异步恢复图执行")
    void handleTodo_winnerClaimsAndResumes() {
        stubApprover();
        when(todoMapper.selectById(TODO_ID)).thenReturn(pendingTodo());
        when(runMapper.selectById(RUN_ID)).thenReturn(waitingRun());
        when(todoMapper.update(any(), any())).thenReturn(1);
        AiAgentRun finished = new AiAgentRun();
        finished.setId(RUN_ID);
        finished.setStatus(AiAgentRun.STATUS_SUCCEEDED);
        when(graphRunner.resume(any(), anyBoolean(), any())).thenReturn(finished);

        AiAgentRun result = service.handleTodo(TODO_ID, true, "同意");

        assertEquals(AiAgentRun.STATUS_RUNNING, result.getStatus(), "胜者视角运行转 RUNNING");
        // 抢占条件：WHERE 带 id + status=PENDING，写入值含 APPROVED 结论
        ArgumentCaptor<LambdaUpdateWrapper<AiAgentTodo>> wrapperCaptor =
                ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(todoMapper).update(any(), wrapperCaptor.capture());
        String sqlSegment = wrapperCaptor.getValue().getSqlSegment();
        assertTrue(sqlSegment.contains("status"), "抢占条件须含 status，实际: " + sqlSegment);
        assertTrue(wrapperCaptor.getValue().getParamNameValuePairs().containsValue(AiAgentTodo.STATUS_PENDING),
                "抢占条件值须为 PENDING");
        assertTrue(wrapperCaptor.getValue().getParamNameValuePairs().containsValue(AiAgentTodo.STATUS_APPROVED),
                "写入值须为 APPROVED 结论");
        // 恢复执行仅由胜者触发一次
        verify(graphRunner, timeout(3000).times(1)).resume(any(), anyBoolean(), any());
        verify(runMapper).updateById(any(AiAgentRun.class));
    }

    @Test
    @DisplayName("并发双审批：两人都读到 PENDING，仅首个条件更新成功，第二个 0 行命中被拒且不恢复")
    void handleTodo_concurrentLoserRejected() {
        stubApprover();
        // 两个请求的快照读都返回 PENDING/WAITING（读到彼此写入前的状态——mock 每次给新实例，
        // 避免第一次调用就地修改 run.status 干扰第二次的快照读）
        when(todoMapper.selectById(TODO_ID)).thenAnswer(inv -> pendingTodo());
        when(runMapper.selectById(RUN_ID)).thenAnswer(inv -> waitingRun());
        // 第一次条件更新命中 1 行（胜者），第二次 0 行（输家——行已被改为 APPROVED）
        when(todoMapper.update(any(), any())).thenReturn(1, 0);
        AiAgentRun finished = new AiAgentRun();
        finished.setId(RUN_ID);
        when(graphRunner.resume(any(), anyBoolean(), any())).thenReturn(finished);

        AiAgentRun winnerResult = service.handleTodo(TODO_ID, true, "同意");
        assertEquals(AiAgentRun.STATUS_RUNNING, winnerResult.getStatus());

        BusinessException loserEx = assertThrows(BusinessException.class,
                () -> service.handleTodo(TODO_ID, true, "再次同意"));
        assertTrue(loserEx.getMessage().contains("待办已被处理"),
                "输家被拒且不再恢复，实际异常: " + loserEx.getMessage());
        verify(graphRunner, timeout(3000).times(1)).resume(any(), anyBoolean(), any());
        verify(runMapper, times(1)).updateById(any(AiAgentRun.class));
    }

    @Test
    @DisplayName("驳回同样走条件更新抢占，写入 REJECTED 结论")
    void handleTodo_rejectWritesRejected() {
        stubApprover();
        when(todoMapper.selectById(TODO_ID)).thenReturn(pendingTodo());
        when(runMapper.selectById(RUN_ID)).thenReturn(waitingRun());
        when(todoMapper.update(any(), any())).thenReturn(1);
        AiAgentRun finished = new AiAgentRun();
        when(graphRunner.resume(any(), anyBoolean(), any())).thenReturn(finished);

        service.handleTodo(TODO_ID, false, "驳回");

        ArgumentCaptor<LambdaUpdateWrapper<AiAgentTodo>> wrapperCaptor =
                ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(todoMapper).update(any(), wrapperCaptor.capture());
        assertTrue(wrapperCaptor.getValue().getParamNameValuePairs().containsValue(AiAgentTodo.STATUS_REJECTED),
                "写入值须为 REJECTED 结论");
        verify(graphRunner, timeout(3000).times(1)).resume(any(), anyBoolean(), any());
    }

    @Test
    @DisplayName("已处理待办（快照即非 PENDING）：前置校验拒绝，不发起条件更新")
    void handleTodo_alreadyProcessedRejected() {
        AiAgentTodo done = pendingTodo();
        done.setStatus(AiAgentTodo.STATUS_APPROVED);
        when(todoMapper.selectById(TODO_ID)).thenReturn(done);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.handleTodo(TODO_ID, false, null));
        assertTrue(ex.getMessage().contains("待办已处理"));
        verify(todoMapper, never()).update(any(), any());
        verify(graphRunner, never()).resume(any(), anyBoolean(), any());
    }

    @Test
    @DisplayName("待办不存在：直接拒绝")
    void handleTodo_todoMissing() {
        when(todoMapper.selectById(TODO_ID)).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.handleTodo(TODO_ID, true, null));
        verify(todoMapper, never()).update(any(), any());
    }

    private AiAgentRun waitingRun() {
        AiAgentRun run = new AiAgentRun();
        run.setId(RUN_ID);
        run.setUserId(1L);
        run.setStatus(AiAgentRun.STATUS_WAITING_APPROVAL);
        return run;
    }
}
