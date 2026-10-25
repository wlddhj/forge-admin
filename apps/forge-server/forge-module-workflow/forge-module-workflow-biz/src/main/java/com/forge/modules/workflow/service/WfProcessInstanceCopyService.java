package com.forge.modules.workflow.service;

import java.util.List;

/**
 * 流程抄送服务接口
 * 抄送记录沿用引擎原生存储：flw_his_task(taskType=cc) + flw_his_task_actor(weight=6)
 *
 * @author forge-admin
 */
public interface WfProcessInstanceCopyService {

    /**
     * 流程结束时自动抄送（按 wf_process_ext.auto_copy_strategy 解析抄送人）
     *
     * @param processInstanceId 流程实例ID
     * @param processDefinitionId 流程定义ID
     * @param reason 抄送原因
     */
    void autoCopyOnProcessEnd(String processInstanceId, String processDefinitionId, String reason);

    /**
     * 手动抄送（运行中实例，发起人或管理员操作）
     *
     * @param processInstanceId 流程实例ID
     * @param copyUserIds 抄送用户ID列表
     * @param reason 抄送原因
     */
    void manualCopy(String processInstanceId, List<Long> copyUserIds, String reason);
}
