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
     * 流程定义名称
     */
    private String processDefinitionName;

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
}
