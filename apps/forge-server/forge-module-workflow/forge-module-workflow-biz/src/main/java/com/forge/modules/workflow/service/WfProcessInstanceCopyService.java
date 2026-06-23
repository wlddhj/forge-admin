package com.forge.modules.workflow.service;

/**
 * 流程抄送服务接口
 *
 * @author forge-admin
 */
public interface WfProcessInstanceCopyService {

    /**
     * 流程结束时自动抄送
     *
     * @param processInstanceId 流程实例ID
     * @param processDefinitionId 流程定义ID
     * @param reason 抄送原因
     */
    void autoCopyOnProcessEnd(String processInstanceId, String processDefinitionId, String reason);

    /**
     * 手动抄送
     *
     * @param processInstanceId 流程实例ID
     * @param copyUserIds 抄送用户ID列表
     * @param reason 抄送原因
     */
    void manualCopy(String processInstanceId, String[] copyUserIds, String reason);
}