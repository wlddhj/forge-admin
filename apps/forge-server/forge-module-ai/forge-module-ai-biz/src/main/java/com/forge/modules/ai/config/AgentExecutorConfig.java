package com.forge.modules.ai.config;

import com.forge.modules.ai.agent.AgentProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import java.util.concurrent.ExecutorService;

/**
 * Agent 专用线程池：工具并行执行、专家子图并行、failover 竞速、SSE 图执行共用，
 * 与 WebClient 的 boundedElastic 隔离，避免并发团队任务耗尽公共线程池。
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
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }

    @Bean("agentScheduler")
    public Scheduler agentScheduler(ThreadPoolTaskExecutor agentExecutor) {
        ExecutorService service = agentExecutor.getThreadPoolExecutor();
        return Schedulers.fromExecutorService(service, "agent-sched");
    }
}
