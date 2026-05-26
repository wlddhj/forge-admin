package com.forge.admin.modules.workflow.framework.candidate;

import java.util.Set;

/**
 * BPM任务候选人策略接口
 *
 * @author forge-admin
 */
public interface BpmTaskCandidateStrategy {

    /**
     * 策略代码
     * 10=角色, 20=部门成员, 21=部门负责人, 30=指定用户, 60=表达式
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
}