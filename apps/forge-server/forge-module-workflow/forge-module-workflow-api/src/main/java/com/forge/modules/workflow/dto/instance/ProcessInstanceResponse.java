package com.forge.modules.workflow.dto.instance;

import lombok.Data;

import java.util.Date;
import java.util.List;

/**
 * 流程实例响应
 *
 * @author forge-admin
 */
@Data
public class ProcessInstanceResponse {

    /**
     * 流程实例ID
     */
    private String id;

    /**
     * 流程定义ID
     */
    private String processDefinitionId;

    /**
     * 流程定义名称
     */
    private String processDefinitionName;

    /**
     * 流程定义Key
     */
    private String processDefinitionKey;

    /**
     * 业务Key
     */
    private String businessKey;

    /**
     * 开始时间
     */
    private Date startTime;

    /**
     * 结束时间
     */
    private Date endTime;

    /**
     * 耗时（毫秒）
     */
    private Long durationInMillis;

    /**
     * 发起人ID
     */
    private String startUserId;

    /**
     * 发起人名称
     */
    private String startUserName;

    /**
     * 流程编号
     */
    private String processNo;

    /**
     * 当前节点名称
     */
    private String currentActivityName;

    /**
     * 当前节点受理人名称列表
     */
    private List<String> currentAssigneeNames;

    /**
     * 当前节点候选人名称列表（未分配处理人时）
     */
    private List<String> currentCandidateNames;

    /**
     * 挂起状态（1激活 2挂起）
     */
    private Integer suspensionState;

    /**
     * 删除原因
     */
    private String deleteReason;
}
