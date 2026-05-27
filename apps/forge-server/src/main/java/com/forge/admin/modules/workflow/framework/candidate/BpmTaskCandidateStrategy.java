package com.forge.admin.modules.workflow.framework.candidate;

import org.flowable.task.service.delegate.DelegateTask;

import java.util.Set;

/**
 * BPM任务候选人策略接口
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
     * @param delegateTask 当前任务
     * @return 用户ID集合
     */
    default Set<Long> calculateUsers(String param, DelegateTask delegateTask) {
        return calculateUsers(param);
    }
}