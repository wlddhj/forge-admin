package com.forge.modules.workflow.config;

import com.alibaba.ttl.TtlRunnable;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * 工作流专用线程池：AI 审批异步执行等 AFTER_COMMIT 事件消费共用，
 * 与 Spring 默认 @Async 执行器及 AI 模块 agentExecutor 隔离，
 * 避免 LLM 长调用阻塞其它异步任务。
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
}
