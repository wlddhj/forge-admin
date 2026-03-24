package com.forge.admin.modules.quartz.config;

import org.quartz.Scheduler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.quartz.SchedulerFactoryBean;

import java.util.Properties;

/**
 * Quartz 配置类
 * 使用 RAMJobStore（内存存储），重启后任务会丢失
 * 如需持久化，可改为使用 JDBC JobStore
 */
@Configuration
public class QuartzConfig {

    /**
     * 配置 SchedulerFactoryBean
     */
    @Bean
    public SchedulerFactoryBean schedulerFactoryBean() {
        SchedulerFactoryBean factory = new SchedulerFactoryBean();

        // Quartz 配置属性
        Properties properties = new Properties();
        // 实例名称
        properties.setProperty("org.quartz.scheduler.instanceName", "forge-adminScheduler");
        properties.setProperty("org.quartz.scheduler.instanceId", "AUTO");
        // 线程池配置
        properties.setProperty("org.quartz.threadPool.class", "org.quartz.simpl.SimpleThreadPool");
        properties.setProperty("org.quartz.threadPool.threadCount", "10");
        properties.setProperty("org.quartz.threadPool.threadPriority", "5");
        // JobStore 配置 - 使用内存存储（简单，重启后任务丢失）
        properties.setProperty("org.quartz.jobStore.class", "org.quartz.simpl.RAMJobStore");

        factory.setQuartzProperties(properties);
        factory.setApplicationContextSchedulerContextKey("applicationContextKey");
        factory.setOverwriteExistingJobs(true);
        factory.setAutoStartup(true);
        factory.setStartupDelay(5);

        return factory;
    }

    /**
     * 获取 Scheduler
     */
    @Bean
    public Scheduler scheduler(SchedulerFactoryBean schedulerFactoryBean) {
        return schedulerFactoryBean.getScheduler();
    }
}
