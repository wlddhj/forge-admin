package com.forge.admin.modules.quartz.service;

import com.forge.admin.modules.quartz.job.QuartzJobExecution;
import com.forge.admin.modules.system.entity.SysJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.*;
import org.springframework.stereotype.Service;

/**
 * 任务调度服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduleService {

    private final Scheduler scheduler;

    private static final String JOB_GROUP_PREFIX = "JOB_GROUP_";

    /**
     * 创建定时任务
     */
    public void scheduleJob(SysJob job) {
        try {
            // 构建 JobDetail
            JobKey jobKey = getJobKey(job);
            JobDetail jobDetail = JobBuilder.newJob(QuartzJobExecution.class)
                    .withIdentity(jobKey)
                    .usingJobData(QuartzJobExecution.createJobDataMap(job))
                    .storeDurably()
                    .build();

            // 构建 CronTrigger
            CronTrigger trigger = TriggerBuilder.newTrigger()
                    .withIdentity(getTriggerKey(job))
                    .withSchedule(CronScheduleBuilder.cronSchedule(job.getCronExpression())
                            .withMisfireHandlingInstructionDoNothing())
                    .build();

            // 检查任务是否已存在
            if (scheduler.checkExists(jobKey)) {
                // 先删除旧任务
                scheduler.deleteJob(jobKey);
            }

            // 调度任务
            scheduler.scheduleJob(jobDetail, trigger);

            // 如果任务状态是暂停，则暂停触发器
            if (job.getStatus() == 0) {
                scheduler.pauseTrigger(getTriggerKey(job));
            }

            log.info("创建定时任务成功: {} - {}", job.getJobName(), job.getCronExpression());

        } catch (Exception e) {
            log.error("创建定时任务失败: {}", job.getJobName(), e);
            throw new RuntimeException("创建定时任务失败: " + e.getMessage());
        }
    }

    /**
     * 暂停定时任务
     */
    public void pauseJob(SysJob job) {
        try {
            JobKey jobKey = getJobKey(job);
            if (scheduler.checkExists(jobKey)) {
                scheduler.pauseJob(jobKey);
                scheduler.pauseTrigger(getTriggerKey(job));
                log.info("暂停定时任务成功: {}", job.getJobName());
            }
        } catch (Exception e) {
            log.error("暂停定时任务失败: {}", job.getJobName(), e);
            throw new RuntimeException("暂停定时任务失败: " + e.getMessage());
        }
    }

    /**
     * 恢复定时任务
     */
    public void resumeJob(SysJob job) {
        try {
            JobKey jobKey = getJobKey(job);
            if (scheduler.checkExists(jobKey)) {
                scheduler.resumeJob(jobKey);
                scheduler.resumeTrigger(getTriggerKey(job));
                log.info("恢复定时任务成功: {}", job.getJobName());
            } else {
                // 任务不存在，重新创建
                scheduleJob(job);
            }
        } catch (Exception e) {
            log.error("恢复定时任务失败: {}", job.getJobName(), e);
            throw new RuntimeException("恢复定时任务失败: " + e.getMessage());
        }
    }

    /**
     * 删除定时任务
     */
    public void deleteJob(SysJob job) {
        try {
            JobKey jobKey = getJobKey(job);
            TriggerKey triggerKey = getTriggerKey(job);

            // 先停止触发器
            scheduler.pauseTrigger(triggerKey);
            // 移除触发器
            scheduler.unscheduleJob(triggerKey);
            // 删除任务
            scheduler.deleteJob(jobKey);

            log.info("删除定时任务成功: {}", job.getJobName());

        } catch (Exception e) {
            log.error("删除定时任务失败: {}", job.getJobName(), e);
            throw new RuntimeException("删除定时任务失败: " + e.getMessage());
        }
    }

    /**
     * 立即执行一次任务
     */
    public void runOnce(SysJob job) {
        try {
            JobKey jobKey = getJobKey(job);

            if (scheduler.checkExists(jobKey)) {
                // 任务存在，直接触发
                scheduler.triggerJob(jobKey);
            } else {
                // 任务不存在，创建后立即执行
                JobDetail jobDetail = JobBuilder.newJob(QuartzJobExecution.class)
                        .withIdentity(jobKey)
                        .usingJobData(QuartzJobExecution.createJobDataMap(job))
                        .storeDurably()
                        .build();

                scheduler.addJob(jobDetail, true);
                scheduler.triggerJob(jobKey);
            }

            log.info("立即执行定时任务: {}", job.getJobName());

        } catch (Exception e) {
            log.error("立即执行定时任务失败: {}", job.getJobName(), e);
            throw new RuntimeException("立即执行定时任务失败: " + e.getMessage());
        }
    }

    /**
     * 更新任务的 cron 表达式
     */
    public void updateJobCron(SysJob job) {
        try {
            TriggerKey triggerKey = getTriggerKey(job);

            if (scheduler.checkExists(triggerKey)) {
                // 构建新的 CronTrigger
                CronTrigger newTrigger = TriggerBuilder.newTrigger()
                        .withIdentity(triggerKey)
                        .withSchedule(CronScheduleBuilder.cronSchedule(job.getCronExpression())
                                .withMisfireHandlingInstructionDoNothing())
                        .build();

                // 重新调度任务
                scheduler.rescheduleJob(triggerKey, newTrigger);
                log.info("更新定时任务cron表达式成功: {} - {}", job.getJobName(), job.getCronExpression());
            }

        } catch (Exception e) {
            log.error("更新定时任务cron表达式失败: {}", job.getJobName(), e);
            throw new RuntimeException("更新定时任务cron表达式失败: " + e.getMessage());
        }
    }

    /**
     * 获取 JobKey
     */
    private JobKey getJobKey(SysJob job) {
        String group = job.getJobGroup() != null ? job.getJobGroup() : JOB_GROUP_PREFIX + job.getId();
        return JobKey.jobKey(job.getId().toString(), group);
    }

    /**
     * 获取 TriggerKey
     */
    private TriggerKey getTriggerKey(SysJob job) {
        String group = job.getJobGroup() != null ? job.getJobGroup() : JOB_GROUP_PREFIX + job.getId();
        return TriggerKey.triggerKey(job.getId().toString(), group);
    }
}
