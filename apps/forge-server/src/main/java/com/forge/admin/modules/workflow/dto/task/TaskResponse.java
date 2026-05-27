package com.forge.admin.modules.workflow.dto.task;

import lombok.Data;

import java.util.Date;
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
     * 流程实例ID
     */
    private String processInstanceId;

    /**
     * 流程定义名称
     */
    private String processDefinitionName;

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
     * 结束时间（历史任务）
     */
    private Date endTime;
}
