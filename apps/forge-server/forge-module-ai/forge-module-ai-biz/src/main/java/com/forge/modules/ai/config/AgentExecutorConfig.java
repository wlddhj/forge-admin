package com.forge.modules.ai.config;

import com.alibaba.ttl.TtlRunnable;
import com.alibaba.ttl.threadpool.TtlExecutors;
import com.forge.modules.ai.agent.AgentProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import java.util.concurrent.ExecutorService;

/**
 * Agent 专用线程池：工具并行执行、专家子图并行、failover 竞速、SSE 图执行共用，
 * 与 WebClient 的 boundedElastic 隔离，避免并发团队任务耗尽公共线程池。
 *
 * <p>租户传递（TENANT_ENABLE=true 前置）：TenantContextHolder 为 TransmittableThreadLocal，
 * 池线程经 TtlRunnable 装饰（提交时捕获快照、执行时回放、结束后还原）自动继承提交线程租户，
 * 消除池线程"创建时刻滞留租户"的串扰；关闸（上下文为空）时装饰为 no-op。
 */
@Configuration
@RequiredArgsConstructor
public class AgentExecutorConfig {

    private final AgentProperties agentProperties;

    @Bean("agentExecutor")
    public ThreadPoolTaskExecutor agentExecutor() {
        AgentProperties.Executor props = agentProperties.getExecutor();
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(props.getCore());
        executor.setMaxPoolSize(props.getMax());
        executor.setQueueCapacity(props.getQueue());
        executor.setThreadNamePrefix("agent-exec-");
        // 容量满时由提交方在当前线程执行，保证不丢任务
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        // TTL：池线程自动继承提交线程的租户上下文（TtlRunnable::get 兼容 TaskDecorator 签名）
        executor.setTaskDecorator(TtlRunnable::get);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }

    @Bean("agentScheduler")
    public Scheduler agentScheduler(ThreadPoolTaskExecutor agentExecutor, AiMetrics aiMetrics) {
        aiMetrics.registerExecutorGauges("agentExecutor", agentExecutor);
        // Reactor 直接包原生池会绕过 TaskDecorator，须再包 TtlExecutors 保证租户传递
        ExecutorService service = TtlExecutors.getTtlExecutorService(agentExecutor.getThreadPoolExecutor());
        return Schedulers.fromExecutorService(service, "agent-sched");
    }

    /**
     * 工具并行/竞速专用池（SynchronousQueue：提交即派发，max 真实生效）。
     * 与 agentExecutor（图执行/专家子图）隔离——历史死锁形态：图执行线程在
     * agentExecutor 内 join 等待本池任务，当池线程全被"图执行+join"占满时
     * 工具任务永远排队（旧池 queue=200 使 max 永不扩容，并发 ≥core 即互等死锁）。
     * CallerRuns 兜底：池满时回退提交线程执行（图线程慢化但不死锁）。
     */
    @Bean("toolExecutor")
    public ThreadPoolTaskExecutor toolExecutor() {
        AgentProperties.Executor props = agentProperties.getExecutor();
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(Math.max(2, props.getCore() / 2));
        executor.setMaxPoolSize(Math.max(4, props.getMax()));
        // 0 = SynchronousQueue：无排队，按需扩到 max
        executor.setQueueCapacity(0);
        executor.setThreadNamePrefix("agent-tool-");
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        executor.setTaskDecorator(TtlRunnable::get);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
