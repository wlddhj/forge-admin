package com.forge.modules.workflow.framework.candidate;

import java.util.Map;
import java.util.Set;

/**
 * BPM任务候选人策略接口 - FlowLong 版本
 *
 * @author forge-admin
 */
public interface BpmTaskCandidateStrategy {

    /**
     * 策略代码
     *
     * @return 策略代码
     */
    int getStrategy();

    /**
     * 策略描述
     *
     * @return 策略描述
     */
    String getDescription();

    /**
     * 根据参数计算候选人用户ID集合
     *
     * @param param 策略参数（逗号分隔的ID或表达式）
     * @return 用户ID集合
     */
    Set<Long> calculateUsers(String param);

    /**
     * 根据参数和任务上下文计算候选人用户ID集合
     * 需要运行时上下文（如发起人信息）的策略应重写此方法
     *
     * @param param        策略参数
     * @param taskContext 任务上下文
     * @return 用户ID集合
     */
    default Set<Long> calculateUsers(String param, TaskContext taskContext) {
        return calculateUsers(param);
    }

    /**
     * 任务上下文接口
     * 用于传递任务相关的上下文信息（替代 Flowable 的 DelegateTask）
     */
    interface TaskContext {

        /**
         * 获取任务ID
         */
        Long getTaskId();

        /**
         * 获取任务定义Key
         */
        String getTaskKey();

        /**
         * 获取任务名称
         */
        String getTaskName();

        /**
         * 获取流程实例ID
         */
        Long getInstanceId();

        /**
         * 获取流程定义ID
         */
        Long getProcessId();

        /**
         * 获取流程发起人ID
         */
        Long getStartUserId();

        /**
         * 获取流程变量
         */
        Map<String, Object> getVariables();

        /**
         * 获取业务Key
         */
        String getBusinessKey();
    }
}