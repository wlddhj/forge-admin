package com.forge.modules.workflow.config;

import com.forge.framework.redis.lock.DistributedLock;
import com.forge.framework.redis.lock.RedisDistributedLock;
import com.forge.modules.workflow.scheduler.WorkflowReminderJob;
import com.forge.modules.workflow.scheduler.WorkflowTimeoutJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.quartz.SpringBeanJobFactory;

/**
 * FlowLong 调度器配置
 *
 * 注册超时检查和提醒检查的 Quartz 定时任务
 *
 * 注意：Quartz Job 使用 ApplicationContextAware 机制获取 Spring Bean
 *
 * @author forge-admin
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "flowlong.scheduler", name = "enabled", havingValue = "true", matchIfMissing = true)
public class FlowLongSchedulerConfig {

    private final FlowLongSchedulerProperties properties;
    private final ApplicationContext applicationContext;

    /**
     * 分布式锁已由 forge-spring-boot-starter-redis 自动配置
     * 这里仅用于确认配置信息
     */
    @Bean
    public DistributedLock workflowDistributedLock(RedisDistributedLock redisDistributedLock) {
        log.info("FlowLong 调度器使用 Redis 分布式锁: lockPrefix=forge:lock:");
        return redisDistributedLock;
    }

    /**
     * 注册超时检查 JobDetail
     */
    @Bean
    public JobDetail timeoutJobDetail() {
        // 将 ApplicationContext 放入 JobDataMap
        JobDataMap jobDataMap = new JobDataMap();
        jobDataMap.put("applicationContext", applicationContext);

        return JobBuilder.newJob(WorkflowTimeoutJob.class)
                .withIdentity("workflowTimeoutJob", "FLOWLONG")
                .withDescription("工作流任务超时检查")
                .storeDurably()
                .requestRecovery()
                .usingJobData(jobDataMap)
                .build();
    }

    /**
     * 注册提醒检查 JobDetail
     */
    @Bean
    public JobDetail reminderJobDetail() {
        // 将 ApplicationContext 放入 JobDataMap
        JobDataMap jobDataMap = new JobDataMap();
        jobDataMap.put("applicationContext", applicationContext);

        return JobBuilder.newJob(WorkflowReminderJob.class)
                .withIdentity("workflowReminderJob", "FLOWLONG")
                .withDescription("工作流任务提醒检查")
                .storeDurably()
                .requestRecovery()
                .usingJobData(jobDataMap)
                .build();
    }

    /**
     * 配置超时检查触发器
     */
    @Bean
    public Trigger timeoutTrigger(JobDetail timeoutJobDetail) {
        int intervalSeconds = properties.getTimeoutCheckInterval();

        log.info("配置超时检查触发器: interval={}s", intervalSeconds);

        SimpleScheduleBuilder scheduleBuilder = SimpleScheduleBuilder
                .simpleSchedule()
                .withIntervalInSeconds(intervalSeconds)
                .repeatForever()
                .withMisfireHandlingInstructionNowWithRemainingCount();

        return TriggerBuilder.newTrigger()
                .forJob(timeoutJobDetail)
                .withIdentity("workflowTimeoutTrigger", "FLOWLONG")
                .withDescription("工作流超时检查触发器")
                .withSchedule(scheduleBuilder)
                .startNow()
                .build();
    }

    /**
     * 配置提醒检查触发器
     */
    @Bean
    public Trigger reminderTrigger(JobDetail reminderJobDetail) {
        int intervalSeconds = properties.getReminderCheckInterval();

        log.info("配置提醒检查触发器: interval={}s", intervalSeconds);

        SimpleScheduleBuilder scheduleBuilder = SimpleScheduleBuilder
                .simpleSchedule()
                .withIntervalInSeconds(intervalSeconds)
                .repeatForever()
                .withMisfireHandlingInstructionNowWithRemainingCount();

        return TriggerBuilder.newTrigger()
                .forJob(reminderJobDetail)
                .withIdentity("workflowReminderTrigger", "FLOWLONG")
                .withDescription("工作流提醒检查触发器")
                .withSchedule(scheduleBuilder)
                .startNow()
                .build();
    }
}