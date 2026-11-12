package com.forge.modules.workflow.config;

import com.alibaba.ttl.TtlRunnable;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 工作流专用线程池：AI 审批异步执行等 AFTER_COMMIT 事件消费共用，
 * 与 Spring 默认 @Async 执行器及 AI 模块 agentExecutor 隔离，
 * 避免 LLM 长调用阻塞其它异步任务。业务回调投递走独立 callbackExecutor，
 * 重试等待由 callbackRetryScheduler 调度，均不占事件消费线程。
 *
 * <p>租户传递（TENANT_ENABLE=true 前置）：IM 通知/业务回调/事件审计的消费线程
 * 经 TtlRunnable 装饰自动继承提交线程（请求/引擎线程）的租户上下文，
 * 消除池线程"创建时刻滞留租户"导致的跨租户串扰；关闸时装饰为 no-op。
 */
@Configuration
public class WorkflowAsyncConfig {

    @Bean("workflowExecutor")
    public ThreadPoolTaskExecutor workflowExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("wf-exec-");
        // 容量满时由提交方在当前线程执行，保证不丢任务
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        // TTL：池线程自动继承提交线程的租户上下文（TtlRunnable::get 兼容 TaskDecorator 签名）
        executor.setTaskDecorator(TtlRunnable::get);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }

    /**
     * LLM 决策专用池（AI 审批/路由的超时执行）：LLM 调用秒级到分钟级，
     * 必须与 workflowExecutor（AFTER_COMMIT 事件消费）隔离，避免长调用堵死事件消费；
     * Abort 拒绝策略使提交失败走既有超时/异常降级路径（而非 CallerRuns 反噬消费线程）。
     * 历史问题：此前 supplyAsync 默认 commonPool——超时后 cancel(true) 不会中断底层调用，
     * 每笔超时都在 commonPool 留一个持续烧到 LLM 端超时的线程（并发超时即 commonPool 饥饿）。
     */
    @Bean("decisionExecutor")
    public ThreadPoolTaskExecutor decisionExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("wf-decision-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        // TTL：决策链租户上下文随任务传递
        executor.setTaskDecorator(TtlRunnable::get);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        return executor;
    }

    /**
     * 业务回调投递专用池：与 workflowExecutor（审批事件消费）隔离，
     * 回调对端慢/挂不再占住事件消费线程（此前在监听线程同步 sleep 重试）；
     * Abort 拒绝策略：池+队列满时提交抛 RejectedExecutionException，
     * 由 CallbackDispatcher 按一次失败计入既有重试链（不丢任务也不反噬提交线程）。
     */
    @Bean("callbackExecutor")
    public ThreadPoolTaskExecutor callbackExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("wf-callback-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        // TTL：回调投递租户上下文随任务传递
        executor.setTaskDecorator(TtlRunnable::get);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        return executor;
    }

    /**
     * 回调重试调度器：单线程承载延迟重试的等待（schedule 定时再提交投递池），
     * 取代在投递线程里 Thread.sleep 的实现，重试等待不再占用任何 worker 线程。
     */
    @Bean(destroyMethod = "shutdown")
    public ScheduledExecutorService callbackRetryScheduler() {
        return Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "wf-callback-retry");
            t.setDaemon(true);
            return t;
        });
    }
}
