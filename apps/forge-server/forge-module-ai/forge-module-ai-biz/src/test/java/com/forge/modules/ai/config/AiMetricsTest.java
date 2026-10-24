package com.forge.modules.ai.config;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 指标门面单测：registry 存在时五类指标注册计数；缺失时 no-op 不抛（AC3）
 */
class AiMetricsTest {

    private AiMetrics withRegistry(MeterRegistry registry) {
        return new AiMetrics(new ObjectProvider<>() {
            @Override
            public MeterRegistry getIfAvailable() {
                return registry;
            }

            @Override
            public MeterRegistry getObject() {
                return registry;
            }
        });
    }

    @Test
    void llm计时与成败标签() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        AiMetrics metrics = withRegistry(registry);
        metrics.llmCall("decision", "glm", true, 120);
        metrics.llmCall("decision", "glm", false, 200);

        assertEquals(2, registry.get("ai.llm.call").timers().stream()
                .mapToLong(t -> t.count()).sum());
        assertEquals(1, registry.get("ai.llm.call")
                .tag("outcome", "success").timer().count());
        assertEquals(1, registry.get("ai.llm.call")
                .tag("outcome", "failure").timer().count());
    }

    @Test
    void failover与工具与缓存计数() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        AiMetrics metrics = withRegistry(registry);
        metrics.failover("switched");
        metrics.failover("switched");
        metrics.failover("exhausted");
        metrics.toolCall("sql_query", true, 50);
        metrics.toolCall("sql_query", false, 10);
        metrics.cacheResult("image", true);
        metrics.cacheResult("image", false);

        assertEquals(2, registry.get("ai.llm.failover").tag("outcome", "switched").counter().count());
        assertEquals(1, registry.get("ai.llm.failover").tag("outcome", "exhausted").counter().count());
        assertEquals(1, registry.get("ai.tool.call").tag("outcome", "success").timer().count());
        assertEquals(1, registry.get("ai.tool.call").tag("outcome", "failure").timer().count());
        assertEquals(1, registry.get("ai.cache.result").tag("outcome", "hit").counter().count());
        assertEquals(1, registry.get("ai.cache.result").tag("outcome", "miss").counter().count());
    }

    @Test
    void executorGauge注册() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        AiMetrics metrics = withRegistry(registry);
        org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor executor =
                new org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor();
        executor.initialize();
        metrics.registerExecutorGauges("agentExecutor", executor);

        assertEquals(0, registry.get("ai.executor.active").gauge().value());
        assertEquals(0, registry.get("ai.executor.queue.size").gauge().value());
    }

    @Test
    void registry缺失时全部noOp不抛() {
        AiMetrics metrics = new AiMetrics(new ObjectProvider<>() {
            @Override
            public MeterRegistry getIfAvailable() {
                return null;
            }
        });
        metrics.llmCall("chat", "m", true, 1);
        metrics.failover("switched");
        metrics.toolCall("t", false, 1);
        metrics.cacheResult("image", true);
        assertNull(new SimpleMeterRegistry().find("ai.llm.call").timer());
    }
}
