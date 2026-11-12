package com.forge.modules.workflow.config;

import com.forge.modules.workflow.scheduler.WorkflowReminderJob;
import com.forge.modules.workflow.scheduler.WorkflowTimeoutJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * FlowLong 调度器配置
 *
 * 注册超时检查和提醒检查的 Quartz 定时任务
 *
 * 注意：Quartz Job 从 SchedulerContext 的 applicationContextKey 获取 Spring 容器
 * （由 forge-module-system 的 QuartzConfig.setApplicationContextSchedulerContextKey 装配）
 *
 * @author forge-admin
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "flowlong.scheduler", name = "enabled", havingValue = "true", matchIfMissing = true)
public class FlowLongSchedulerConfig {

    private final FlowLongSchedulerProperties properties;

    /**
     * 注册超时检查 JobDetail
     */
    @Bean
    public JobDetail timeoutJobDetail() {
        return JobBuilder.newJob(WorkflowTimeoutJob.class)
                .withIdentity("workflowTimeoutJob", "FLOWLONG")
                .withDescription("工作流任务超时检查")
                .storeDurably()
                .requestRecovery()
                .build();
    }

    /**
     * 注册提醒检查 JobDetail
     */
    @Bean
    public JobDetail reminderJobDetail() {
        return JobBuilder.newJob(WorkflowReminderJob.class)
                .withIdentity("workflowReminderJob", "FLOWLONG")
                .withDescription("工作流任务提醒检查")
                .storeDurably()
                .requestRecovery()
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