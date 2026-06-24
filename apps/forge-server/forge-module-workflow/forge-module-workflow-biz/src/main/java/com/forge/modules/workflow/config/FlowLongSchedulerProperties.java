package com.forge.modules.workflow.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * FlowLong 调度器配置属性
 *
 * @author forge-admin
 */
@Data
@Component
@ConfigurationProperties(prefix = "flowlong.scheduler")
public class FlowLongSchedulerProperties {

    /**
     * 是否启用调度器
     */
    private boolean enabled = true;

    /**
     * 超时检查间隔（秒）
     * 用于检查任务是否超时并执行自动处理
     */
    private int timeoutCheckInterval = 60;

    /**
     * 提醒检查间隔（秒）
     * 用于检查任务是否需要发送提醒
     */
    private int reminderCheckInterval = 300;

    /**
     * 提醒提前时间（分钟）
     * 在任务即将超时前多少分钟发送提醒
     */
    private int reminderAdvanceMinutes = 30;

    /**
     * 是否使用分布式锁
     * 防止多实例重复执行
     */
    private boolean useDistributedLock = true;

    /**
     * 分布式锁类型
     * 支持：redis、database
     */
    private String lockType = "redis";

    /**
     * 锁超时时间（秒）
     */
    private int lockTimeoutSeconds = 30;

    /**
     * 最大提醒次数
     * 达到后不再发送提醒
     */
    private int maxReminderCount = 3;

    /**
     * 提醒间隔（小时）
     * 每次提醒之间的间隔
     */
    private int reminderIntervalHours = 24;

    /**
     * 工作时间配置
     * 格式：8:00-18:00，只在工作时间发送提醒
     */
    private String workTime;

    /**
     * 工作日配置
     * 格式：1,2,3,4,5（周一到周五）
     */
    private String workDays;
}