package com.forge.modules.ai.config;

import com.forge.framework.tenant.core.context.TenantContextHolder;
import com.forge.modules.ai.agent.AgentProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * agentExecutor TTL 传递单元测试（租户开闸前置 T1）：
 * 池线程自动继承提交线程租户快照，任务结束不泄漏到同池后续任务。
 */
class AgentExecutorTtlTest {

    private ThreadPoolTaskExecutor executor;

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
        if (executor != null) {
            executor.shutdown();
        }
    }

    private ThreadPoolTaskExecutor build() {
        AgentProperties properties = new AgentProperties();
        AgentExecutorConfig config = new AgentExecutorConfig(properties);
        executor = config.agentExecutor();
        return executor;
    }

    @Test
    @DisplayName("提交线程租户快照传递到池线程")
    void tenantShouldPropagateToPoolThread() throws Exception {
        ThreadPoolTaskExecutor pool = build();
        TenantContextHolder.setTenantId(2L);
        CompletableFuture<Long> captured = pool.submitCompletable(TenantContextHolder::getTenantId);
        assertEquals(2L, captured.get(5, TimeUnit.SECONDS));
    }

    @Test
    @DisplayName("任务结束后租户不泄漏：无上下文提交的后续任务读到 null")
    void tenantShouldNotLeakToNextTask() throws Exception {
        ThreadPoolTaskExecutor pool = build();
        TenantContextHolder.setTenantId(2L);
        pool.submitCompletable(TenantContextHolder::getTenantId).get(5, TimeUnit.SECONDS);
        TenantContextHolder.clear();

        // 同一池线程可能复用：新任务（无租户提交）必须读到 null 而非残留 2
        for (int i = 0; i < 5; i++) {
            CompletableFuture<Long> next = pool.submitCompletable(TenantContextHolder::getTenantId);
            assertNull(next.get(5, TimeUnit.SECONDS), "池线程不得残留上一任务的租户");
        }
    }

    @Test
    @DisplayName("agentScheduler（Reactor 包装）同样传递租户")
    void schedulerShouldPropagateTenant() {
        AgentProperties properties = new AgentProperties();
        AgentExecutorConfig config = new AgentExecutorConfig(properties);
        ThreadPoolTaskExecutor pool = config.agentExecutor();
        try {
            var scheduler = config.agentScheduler(pool, new AiMetrics(new org.springframework.beans.factory.ObjectProvider<>() {
                @Override
                public io.micrometer.core.instrument.MeterRegistry getIfAvailable() {
                    return null;
                }

                @Override
                public io.micrometer.core.instrument.MeterRegistry getObject() {
                    throw new UnsupportedOperationException();
                }
            }));
            TenantContextHolder.setTenantId(3L);
            Long captured = Mono.fromCallable(TenantContextHolder::getTenantId)
                    .subscribeOn(scheduler)
                    .block(Duration.ofSeconds(5));
            assertEquals(3L, captured);
        } finally {
            TenantContextHolder.clear();
            pool.shutdown();
        }
    }
}
