package com.forge.modules.ai.config;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * AI 模块指标门面：LLM 调用/故障转移/工具执行/缓存命中计时计数与线程池水位 gauge。
 * MeterRegistry 可选注入（ObjectProvider）——无 actuator 的测试/裁剪部署全量 no-op，业务零影响。
 * 指标清单：
 *   ai.llm.call   timer  tag: kind(chat|decision) model outcome(success|failure)
 *   ai.llm.failover counter tag: outcome(switched|exhausted)
 *   ai.tool.call  timer  tag: tool outcome(success|failure)
 *   ai.cache.result counter tag: cache(image|...) outcome(hit|miss)
 *   ai.executor.active|pool.size|queue.size gauge
 */
@Slf4j
@Component
public class AiMetrics {

    private final MeterRegistry registry;

    public AiMetrics(ObjectProvider<MeterRegistry> registryProvider) {
        this.registry = registryProvider.getIfAvailable();
        if (registry == null) {
            log.info("未检测到 MeterRegistry，AI 指标组件以 no-op 模式运行");
        }
    }

    public void llmCall(String kind, String model, boolean success, long durationMs) {
        if (registry == null) {
            return;
        }
        Timer.builder("ai.llm.call")
                .tags(Tags.of(Tag.of("kind", kind), Tag.of("model", model),
                        Tag.of("outcome", success ? "success" : "failure")))
                .register(registry)
                .record(durationMs, TimeUnit.MILLISECONDS);
    }

    public void failover(String outcome) {
        if (registry == null) {
            return;
        }
        registry.counter("ai.llm.failover", "outcome", outcome).increment();
    }

    public void toolCall(String tool, boolean success, long durationMs) {
        if (registry == null) {
            return;
        }
        Timer.builder("ai.tool.call")
                .tags(Tags.of(Tag.of("tool", tool), Tag.of("outcome", success ? "success" : "failure")))
                .register(registry)
                .record(durationMs, TimeUnit.MILLISECONDS);
    }

    public void cacheResult(String cache, boolean hit) {
        if (registry == null) {
            return;
        }
        registry.counter("ai.cache.result", "cache", cache, "outcome", hit ? "hit" : "miss").increment();
    }

    /** agentExecutor 线程池水位 gauge（引用池内部原子量，常驻弱引用） */
    public void registerExecutorGauges(String name, ThreadPoolTaskExecutor executor) {
        if (registry == null) {
            return;
        }
        var pool = executor.getThreadPoolExecutor();
        registry.gauge("ai.executor.active", Tags.of(Tag.of("name", name)),
                pool, p -> p.getActiveCount());
        registry.gauge("ai.executor.pool.size", Tags.of(Tag.of("name", name)),
                pool, p -> p.getPoolSize());
        registry.gauge("ai.executor.queue.size", Tags.of(Tag.of("name", name)),
                pool, p -> p.getQueue().size());
    }
}
