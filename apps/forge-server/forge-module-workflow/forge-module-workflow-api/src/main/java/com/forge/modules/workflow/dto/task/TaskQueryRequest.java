package com.forge.modules.workflow.dto.task;

import lombok.Data;

/**
 * 任务查询请求
 *
 * @author forge-admin
 */
@Data
public class TaskQueryRequest {

    /**
     * 当前页
     */
    private int pageNum = 1;

    /**
     * 每页大小
     */
    private int pageSize = 20;

    /**
     * 任务名称
     */
    private String name;

    /**
     * 流程定义名称
     */
    private String processDefinitionName;
}
