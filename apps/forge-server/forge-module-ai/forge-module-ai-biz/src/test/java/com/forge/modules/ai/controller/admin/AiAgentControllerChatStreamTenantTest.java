package com.forge.modules.ai.controller.admin;

import com.forge.common.utils.UserContext;
import com.forge.framework.tenant.core.context.TenantContextHolder;
import com.forge.modules.ai.agent.kernel.AgentGraphRunner;
import com.forge.modules.ai.agent.lifecycle.AgentDataCleanupService;
import com.forge.modules.ai.entity.AiAgentConfig;
import com.forge.modules.ai.entity.AiAgentRun;
import com.forge.modules.ai.service.AiAgentConfigService;
import com.forge.modules.ai.service.AiAgentRunService;
import com.forge.modules.ai.service.AiAgentTeamService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

/**
 * chatStream 租户快照单测：Flux.create 订阅在 agentScheduler 池线程执行，
 * 请求线程租户须显式传递（池线程读到快照值，结束后不残留）。
 */
class AiAgentControllerChatStreamTenantTest {

    private static final long TENANT_ID = 2L;

    private AiAgentConfigService agentConfigService;
    private AgentGraphRunner graphRunner;
    private AiAgentController controller;
    private ThreadPoolExecutor schedulerExecutor;
    private Scheduler agentScheduler;

    @BeforeEach
    void setUp() {
        agentConfigService = mock(AiAgentConfigService.class);
        AiAgentRunService agentRunService = mock(AiAgentRunService.class);
        AiAgentTeamService teamService = mock(AiAgentTeamService.class);
        graphRunner = mock(AgentGraphRunner.class);
        AgentDataCleanupService cleanupService = mock(AgentDataCleanupService.class);

        // 单线程池（提交前预热）：调度线程与请求线程隔离，租户只可能来自快照传递
        schedulerExecutor = new ThreadPoolExecutor(1, 1,
                0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>());
        schedulerExecutor.prestartAllCoreThreads();
        agentScheduler = Schedulers.fromExecutorService(schedulerExecutor);
        controller = new AiAgentController(agentConfigService, agentRunService, teamService,
                graphRunner, new ObjectMapper(), cleanupService, agentScheduler);

        AiAgentConfig config = new AiAgentConfig();
        config.setId(1L);
        config.setAgentCode("demo");
        lenient().when(agentConfigService.listEnabled()).thenReturn(List.of(config));
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
        TenantContextHolder.clear();
        schedulerExecutor.shutdownNow();
    }

    @Test
    void chatStream池线程读到请求线程租户快照且结束不残留() throws Exception {
        UserContext user = new UserContext();
        user.setUserId(9L);
        UserContext.set(user);
        TenantContextHolder.setTenantId(TENANT_ID);

        CountDownLatch runStreamInvoked = new CountDownLatch(1);
        AtomicReference<Long> capturedTenant = new AtomicReference<>(-1L);
        AtomicReference<String> capturedThread = new AtomicReference<>();
        lenient().when(graphRunner.runStream(any(), anyLong(), anyLong(), anyString(), anyString(),
                anyList(), any())).thenAnswer(invocation -> {
            // 此处运行在 agentScheduler 池线程：断言租户快照已回放
            capturedTenant.set(TenantContextHolder.getTenantId());
            capturedThread.set(Thread.currentThread().getName());
            runStreamInvoked.countDown();
            return new AiAgentRun();
        });

        AiAgentController.AgentChatRequest request = new AiAgentController.AgentChatRequest();
        request.setAgentCode("demo");
        request.setConversationId(5L);
        request.setContent("你好");
        request.setImages(List.of());

        controller.chatStream(request);

        assertTrue(runStreamInvoked.await(5, TimeUnit.SECONDS), "runStream 未在调度线程执行");
        assertEquals(TENANT_ID, capturedTenant.get(), "池线程应读到请求线程租户快照");
        assertNotEquals(Thread.currentThread().getName(), capturedThread.get(), "图执行应发生在调度线程");
        // 泄漏检查：调度线程结束后不残留租户（finally 清理生效）
        assertNull(schedulerExecutor.submit(TenantContextHolder::getTenantId).get(5, TimeUnit.SECONDS),
                "调度线程不得残留请求租户");
        assertTrue(Objects.equals(TENANT_ID, TenantContextHolder.getTenantId()), "请求线程租户不受影响");
    }
}
