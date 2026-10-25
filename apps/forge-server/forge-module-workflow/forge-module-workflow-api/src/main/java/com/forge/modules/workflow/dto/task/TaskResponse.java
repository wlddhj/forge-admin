package com.forge.modules.workflow.dto.task;

import lombok.Data;

import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * 任务响应
 *
 * @author forge-admin
 */
@Data
public class TaskResponse {

    /**
     * 任务ID
     */
    private String id;

    /**
     * 任务名称
     */
    private String name;

    /**
     * 任务定义Key
     */
    private String taskDefinitionKey;

    /**
     * 流程编号
     */
    private String processNo;

    /**
     * 流程实例ID
     */
    private String processInstanceId;

    /**
     * 流程定义名称
     */
    private String processDefinitionName;

    /**
     * 流程定义ID
     */
    private String processDefinitionId;

    /**
     * 流程定义Key
     */
    private String processDefinitionKey;

    /**
     * 发起人ID
     */
    private String startUserId;

    /**
     * 发起人名称
     */
    private String startUserName;

    /**
     * 流程发起时间
     */
    private Date startTime;

    /**
     * 处理人ID
     */
    private String assignee;

    /**
     * 处理人名称
     */
    private String assigneeName;

    /**
     * 拥有者ID
     */
    private String owner;

    /**
     * 拥有者名称
     */
    private String ownerName;

    /**
     * 创建时间
     */
    private Date createTime;

    /**
     * 签收时间
     */
    private Date claimTime;

    /**
     * 到期日期
     */
    private Date dueDate;

    /**
     * 分类
     */
    private String category;

    /**
     * 任务变量
     */
    private Map<String, Object> variables;

    /**
     * 审批时间（已办任务，取审批意见表的创建时间）
     */
    private Date endTime;

    /**
     * 是否为候选任务（未认领）
     */
    private Boolean candidate;

    /**
     * 当前用户是否从候选组（角色/部门）签收（待办行展示“取消签收”入口）
     */
    private Boolean claimedFromGroup;

    /**
     * 候选人名称列表
     */
    private List<String> candidateUsers;

    /**
     * 审批动作（已办任务）
     */
    private String actionType;

    /**
     * 审批意见（已办任务）
     */
    private String commentText;

    /**
     * 流转节点名称（已办任务审批后流转到的下一节点）
     */
    private String nextActivityName;

    /**
     * 是否可拿回（已办任务：下一节点待人审批任务尚未办理）
     */
    private Boolean canReclaim;

    /**
     * 是否已被撤回（已办任务）
     */
    private Boolean withdrawn;
}
