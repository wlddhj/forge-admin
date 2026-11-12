package com.forge.modules.workflow.config;

import com.forge.framework.tenant.core.context.TenantContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * workflowExecutor TTL 传递单元测试（租户开闸前置 T1）：
 * IM/回调/事件审计消费线程自动继承提交线程租户，任务结束不泄漏。
 */
class WorkflowExecutorTtlTest {

    private ThreadPoolTaskExecutor executor;

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
        if (executor != null) {
            executor.shutdown();
        }
    }

    private ThreadPoolTaskExecutor build() {
        executor = new WorkflowAsyncConfig().workflowExecutor();
        return executor;
    }

    @Test
    @DisplayName("提交线程租户快照传递到池线程（AFTER_COMMIT 消费链租户来源）")
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

        for (int i = 0; i < 5; i++) {
            CompletableFuture<Long> next = pool.submitCompletable(TenantContextHolder::getTenantId);
            assertNull(next.get(5, TimeUnit.SECONDS), "池线程不得残留上一任务的租户");
        }
    }
}
