package com.forge.modules.workflow.dto.instance;

import lombok.Data;

/**
 * 流程实例查询请求
 *
 * @author forge-admin
 */
@Data
public class ProcessInstanceQueryRequest {

    /**
     * 当前页
     */
    private int pageNum = 1;

    /**
     * 每页大小
     */
    private int pageSize = 20;

    /**
     * 流程定义ID
     */
    private String processDefinitionId;

    /**
     * 流程名称（模糊搜索）
     */
    private String processName;

    /**
     * 流程定义名称
     */
    private String processDefinitionName;

    /**
     * 流程标题关键字（实例变量 __title 模糊匹配，通配符按字面处理）
     */
    private String title;

    /**
     * 发起人名称
     */
    private String startUserName;

    /**
     * 开始时间（起始）
     */
    private String startTimeBegin;

    /**
     * 开始时间（截止）
     */
    private String startTimeEnd;

    /**
     * 状态：running-运行中, finished-已完成, terminated-已终止
     */
    private String status;

    /**
     * 业务Key（精确匹配）
     */
    private String businessKey;

    /**
     * 优先级筛选
     */
    private Integer priority;

    /**
     * 是否按优先级排序（优先级高的在前）
     */
    private Boolean sortByPriority;
}
