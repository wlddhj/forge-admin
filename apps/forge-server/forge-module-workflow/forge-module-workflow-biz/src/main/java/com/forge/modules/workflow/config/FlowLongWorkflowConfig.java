package com.forge.modules.workflow.config;

import com.aizuda.bpm.engine.listener.TaskListener;
import com.forge.modules.workflow.framework.listener.BpmTaskCandidateListener;
import com.forge.modules.workflow.listener.TaskNotificationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * FlowLong 工作流配置类
 * 注册自定义任务监听器
 *
 * @author forge-admin
 */
@Configuration
public class FlowLongWorkflowConfig {

    /**
     * 注册任务监听器列表
     * FlowLong 会自动扫描所有 TaskListener Bean 并在任务创建时调用
     */
    @Bean
    public List<TaskListener> taskListeners(
            BpmTaskCandidateListener candidateListener,
            TaskNotificationListener notificationListener) {
        return List.of(candidateListener, notificationListener);
    }
}